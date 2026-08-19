package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.implementation.reachability.BlackUnboundedReachValues;
import de.tum.in.pet.sampler.UnboundedValues;
import de.tum.in.pet.util.ErrorProbabilityCalculator;
import de.tum.in.pet.util.InPlaceBettingMartingale;
import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.explorer.BlackExplorer;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.graph.Mec;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.Model;
import it.unimi.dsi.fastutil.doubles.Double2LongFunction;
import it.unimi.dsi.fastutil.ints.*;
import prism.Pair;
import prism.PrismException;

import java.util.*;
import java.util.function.IntPredicate;
import java.util.logging.Level;

import static de.tum.in.probmodels.util.Util.isZero;

// better structure
/**
 * Class to facilitate OnDemandValueIteration for Black Box models. An amalgamation of CAV'17 and CAV'19 papers.
 */
public class BlackOnDemandValueIterator<S, M extends Model> extends OnDemandValueIterator<S, M> {

  protected final double pMin; // as mentioned in CAV'19. It should be set to the lowest transition probability of the input model.
  protected final double errorTolerance; // as mentioned in CAV'19. Error tolerance for the learned distributions of the learned model.
  protected final int aggregationCount;
  protected final Double2LongFunction nSampleFunction; // returns N_k for each k as in CAV'19. Returns the number of times paths should be sampled for each value of k.

  protected List<NatBitSet> mecs = new ArrayList<>(); // Holds a list of mecs in the model.
  protected Double transDelta = 1d; // equal to delta_T as mentioned in CAV'19. Error tolerance for each transition of the learned model.

  protected final Int2IntMap stateToMecMap = new Int2IntOpenHashMap(); // Map that returns the mec Index the state is a part of.
  protected Int2ObjectMap<Distribution> stayActionMap = new Int2ObjectOpenHashMap<>(); // Map that holds the stay action for mecs, accessible using mecIndices.

  protected Int2IntMap stayActionCounts = new Int2IntOpenHashMap(); // Map that holds the number of times each stay action for an mec has been sampled, accessible using mecIndices.

  protected boolean seenNewTransitionSignificantly = false; // If a new transition has been sampled a significant number of times.

  // Enable this boolean only when the updateMethod is greyBox.
  private final boolean calculateErrorProbability;
  private final SimulateMec simulateMec;
  private final int maxSuccessorsInModel;
  private final DeltaTCalculationMethod deltaTCalculationMethod;
  private final TransitionProbabilityMethod transitionProbabilityMethod;
  private final IntPredicate target;
  private final boolean runAsReachChecker;

  protected static final double initialNSamples = 1e4;
  protected static final double multiplicativeFactor = 5;

  protected Int2ObjectFunction<Int2ObjectFunction<Int2ObjectFunction<Pair<Double, Double>>>> confidenceWidthFunction;


  protected HashMap<Integer, HashMap<Integer, MartingaleActionStats>> martingaleTransitionCount =
          new HashMap<>();
  protected HashMap<Integer, HashMap<Integer, BernsteinActionStats>> bernsteinTransitionCount =
          new HashMap<>();

  private static final class MartingaleActionStats {
    private final HashMap<Integer, InPlaceBettingMartingale> transitions = new HashMap<>();
    private final int seenState;
    private int secondSeenState = -1;

    private MartingaleActionStats(int seenState) {
      this.seenState = seenState;
    }
  }

  private static final class BernsteinActionStats {
    private final HashMap<Integer, BernsteinTransitionStats> transitions = new HashMap<>();
    private double previousEstimate = 0.0;
  }

  private static final class BernsteinTransitionStats {
    private long count = 0L;
    private double varianceProxy = 0.0;
    private long lastUpdatedAt = 0L;
  }

  public BlackOnDemandValueIterator(Explorer<S, M> explorer, UnboundedValues values, RewardGenerator<S> rewardGenerator,
                                    int revisitThreshold, double rMax, double pMin, double errorTolerance,
                                    Double2LongFunction nSampleFunction, double precision, long numberOfTransitions,
                                    int aggregationCount, long timeout, boolean getErrorProbability, SimulateMec simulateMec,
                                    DeltaTCalculationMethod deltaTCalculationMethod, int maxSuccessorsInModel,
                                    TransitionProbabilityMethod transitionProbabilityMethod, IntPredicate target,
                                    boolean runAsReachChecker) {
    super(explorer, values, rewardGenerator, revisitThreshold, rMax, precision, timeout);
    this.pMin = pMin;
    this.errorTolerance = errorTolerance;
    this.nSampleFunction = nSampleFunction;
    this.calculateErrorProbability = getErrorProbability;
    this.simulateMec = simulateMec;
    this.deltaTCalculationMethod = deltaTCalculationMethod;
    this.maxSuccessorsInModel = maxSuccessorsInModel;
    this.transitionProbabilityMethod = transitionProbabilityMethod;
    this.target = target;
    this.runAsReachChecker = runAsReachChecker;
    if (transitionProbabilityMethod == TransitionProbabilityMethod.Martingale
            || transitionProbabilityMethod == TransitionProbabilityMethod.Bernstein) {
      this.transDelta = errorTolerance / numberOfTransitions;
    }
    this.aggregationCount = aggregationCount;

    BlackUnboundedReachValues blackValues = (BlackUnboundedReachValues) this.values;
    BlackExplorer<S, M> explorer_ = (BlackExplorer<S, M>) explorer();

    // Updates the confidenceWidthFunction according to the latest counts and transDelta value. The confidenceWidthFunction
    // returns the confidenceWidth for a state x and an action with index y. if y is greater than the number of choices
    // the explorer holds, it must be the stay action. We set confidence width of stay action equal to zero as we
    // know the probabilities of the action are accurate as they have been calculated and not learned.
    if (transitionProbabilityMethod == TransitionProbabilityMethod.Hoeffding) {
      confidenceWidthFunction = state -> (action -> nextState -> {
        if (action >= explorer.getChoices(state).size()) {
          return new Pair<>(-1.0, -1.0);
        }
        double confidenceWidth = Math.sqrt(-Math.log(transDelta) / (2 * explorer_.getActionCounts(state, action)));
        double probability = explorer.getChoices(state).get(action).get(nextState);
        return new Pair<>(Math.max(0.0, probability - confidenceWidth),
                Math.min(1.0, probability + confidenceWidth));
      });
    } else if (transitionProbabilityMethod == TransitionProbabilityMethod.Martingale) {
      confidenceWidthFunction = state -> (action -> nextState -> {
        if (action >= explorer.getChoices(state).size()) {
          return new Pair<>(-1.0, -1.0);
        }

        HashMap<Integer, MartingaleActionStats> actions = martingaleTransitionCount.get(state);
        if (actions == null) {
          return new Pair<>(0.0, 1.0);
        }

        MartingaleActionStats actionStats = actions.get(action);
        if (actionStats == null) {
          return new Pair<>(0.0, 1.0);
        }

        int seenState = actionStats.seenState;
        int secondSeenState = actionStats.secondSeenState;
        HashMap<Integer, InPlaceBettingMartingale> transitions = actionStats.transitions;

        if (transitions.containsKey(nextState)) {
          return transitions.get(nextState).getConfidenceWidth();
        }
        if (nextState == seenState || nextState == secondSeenState) {
          Pair<Double, Double> confidence = transitions.get(seenState).getConfidenceWidth();
          return new Pair<>(1 - confidence.second, 1 - confidence.first);
        }
        return new Pair<>(0.0, 1.0);
      });
    } else {
      confidenceWidthFunction = state -> (action -> nextState -> {
        if (action >= explorer.getChoices(state).size()) {
          return new Pair<>(-1.0, -1.0);
        }

        HashMap<Integer, BernsteinActionStats> actions = bernsteinTransitionCount.get(state);
        if (actions == null) {
          return new Pair<>(0.0, 1.0);
        }

        BernsteinActionStats actionStats = actions.get(action);
        if (actionStats == null || !actionStats.transitions.containsKey(nextState)) {
          return new Pair<>(0.0, 1.0);
        }

        BernsteinTransitionStats transitionStats = actionStats.transitions.get(nextState);
        long stateActionCount = 0;
        for (BernsteinTransitionStats nextStateStats : actionStats.transitions.values()) {
          stateActionCount += nextStateStats.count;
        }

        double z = (double) transitionStats.count / (double) stateActionCount;
        if (stateActionCount > transitionStats.lastUpdatedAt) {
          transitionStats.varianceProxy += (1 - actionStats.previousEstimate)
                  * (1 - actionStats.previousEstimate);
          transitionStats.lastUpdatedAt = stateActionCount;
          actionStats.previousEstimate = z;
        }

        double sigma = transDelta;
        double L = Math.log(Math.log(2.0 * Math.max(transitionStats.varianceProxy, 1.0)));
        double diff = (1.7 * Math.sqrt(Math.max(transitionStats.varianceProxy, 1.0) * (L
                + (1.0 / 1.4) * Math.log(2 / sigma) + 1.18))
                + 2.42 * Math.log(2 / sigma) + 3.38 * L + 3.98)
                / (double) stateActionCount;
        return new Pair<>(Math.max(0.0, z - diff), Math.min(1.0, z + diff));
      });
    }

    // Updates the confidence width function in UnboundedReachValues.
    blackValues.setConfidenceWidthFunction(confidenceWidthFunction);
    explorer_.setConfidenceWidthFunction(confidenceWidthFunction);
    if (!runAsReachChecker) {
      initSinkStates();
    }
  }

  @Override
  public Bounds bounds(int state) {
    return values.bounds(state);
  }

  public void updateMartingaleTransitions(int currentState, int actionIndex, int nextState) {
    martingaleTransitionCount.putIfAbsent(currentState, new HashMap<>());
    HashMap<Integer, MartingaleActionStats> actionMartingales =
            martingaleTransitionCount.get(currentState);

    actionMartingales.putIfAbsent(actionIndex, new MartingaleActionStats(nextState));
    MartingaleActionStats actionStats = actionMartingales.get(actionIndex);
    HashMap<Integer, InPlaceBettingMartingale> nextStateMartingale = actionStats.transitions;
    int seenState = actionStats.seenState;
    int secondSeenState = actionStats.secondSeenState;

    int num_next_states = nextStateMartingale.size();
    if (num_next_states == 1) {
      if (seenState == nextState) {
        nextStateMartingale.get(seenState).observe(1);
      } else if (secondSeenState == nextState) {
        nextStateMartingale.get(seenState).observe(0);
      } else if (secondSeenState == -1) {
        actionStats.secondSeenState = nextState;
        nextStateMartingale.get(seenState).observe(0);
      } else {
        // insert two next items into nextStateMartingale, create old samples
        // second item
        nextStateMartingale.put(secondSeenState, new InPlaceBettingMartingale(transDelta, aggregationCount));
        InPlaceBettingMartingale second = nextStateMartingale.get(secondSeenState);
        for (double i: nextStateMartingale.get(seenState).samples.elements)
          second.observe(1 - i); // ???: is this logic sound with aggregation

          // third item
        nextStateMartingale.put(nextState, new InPlaceBettingMartingale(transDelta, aggregationCount));
        InPlaceBettingMartingale third = nextStateMartingale.get(nextState);
        for (int i = 0; i < nextStateMartingale.get(seenState).size(); i++)
          third.observe(0);

        // insert this sample
        nextStateMartingale.get(seenState).observe(0);
        second.observe(0);
        third.observe(1);
      }
      return;
    }

    if (!nextStateMartingale.containsKey(nextState)) {
      nextStateMartingale.put(nextState, new InPlaceBettingMartingale(transDelta, aggregationCount));
      InPlaceBettingMartingale new_obs = nextStateMartingale.get(nextState);
      for (int i = 0; i < nextStateMartingale.get(seenState).size(); i++)
        new_obs.observe(0);
    }

    nextStateMartingale.forEach(
      (state, mart) -> {
        if (state == nextState) {
          mart.observe(1);
        } else {
          mart.observe(0);
        }
      }
    );
  }

  public void updateBernsteinTransitions(int currentState, int actionIndex, int nextState) {
    bernsteinTransitionCount.putIfAbsent(currentState, new HashMap<>());
    HashMap<Integer, BernsteinActionStats> actions = bernsteinTransitionCount.get(currentState);

    actions.putIfAbsent(actionIndex, new BernsteinActionStats());
    BernsteinActionStats actionStats = actions.get(actionIndex);
    actionStats.transitions.putIfAbsent(nextState, new BernsteinTransitionStats());
    actionStats.transitions.get(nextState).count++;
  }

  @Override
  protected boolean sample(int initialState, int run) throws PrismException {

    BlackExplorer<S, M> explorer = (BlackExplorer<S, M>) explorer();

    if (transitionProbabilityMethod == TransitionProbabilityMethod.Hoeffding) {
      computeDeltaT(explorer, errorTolerance, run);
    } else {
      explorer.updateCountParams(transDelta, pMin);
    }

    double k = Math.pow(2, run);
    long nIterations = nSampleFunction.apply(k);

    for (int i = 0; i < nIterations; i++) {
      IntList visitStack = new IntArrayList();
      int currentState = initialState;
      Int2IntOpenHashMap stateVisitCounts = new Int2IntOpenHashMap();  // keeps counts of the number of times a state is visited

      while (true) {
        // Stop simulation if timeout occurred
        if (isTimeout()) {
          return true;
        }

        visitStack.add(currentState);
        stateVisitCounts.putIfAbsent(currentState, 0);
        stateVisitCounts.addTo(currentState, 1);

        // checks plus state,minus state and uncertain state
        if (!runAsReachChecker && BoundedMecQuotient.isSinkState(currentState)) {
          visitStack.removeInt(visitStack.size() - 1);
          // We update the MEC reward bounds through running VI if we reach the uncertain or the plus state. This is
          // slightly different from the version in CAV'17 where VI is only run when the uncertain state is reached.
          // However, this is also OK as reaching the plus state shows that probably the lower reward bound is high
          // enough, meaning the EC is promising and it is worth getting a more precise value. We make sure in updateMEC
          // that we don't get value that is more precise than what is required.
          if (BoundedMecQuotient.isUncertainState(currentState)||BoundedMecQuotient.isPlusState(currentState)) {
            int mecIndex = stateToMecMap.get(visitStack.removeInt(visitStack.size() - 1));
            explorer.activateActionCountFilter();
            updateMec(mecIndex);
            explorer.deactivateActionCountFilter();
          }
          break;
        }

        if (!explorer().isExploredState(currentState)) {
          explore(currentState);  // action choices etc. are populated in the partial model. The bounds of currentState are also initialised.
        }

        if (runAsReachChecker && target.test(currentState)) {
            break;
        }

        List<Distribution> choices = choices(currentState);
        if (choices.isEmpty()){
          break;
        }

        int nextState, nextActionIndex;
        // This condition is there as in the simulate function in CAV'19. It checks whether we have been returning to a
        // state too many times during simulation indicating that we could be stuck inside an MEC.
        if (stateVisitCounts.get(currentState)>=revisitThreshold && looping(visitStack)) {
          Pair<Integer, Integer> bestStateActionPairs = getSampledBestLeavingAction(currentState);
          currentState = bestStateActionPairs.first;
          nextActionIndex = bestStateActionPairs.second;
          if (runAsReachChecker && currentState == -1) {
            // we do not have any of the augmented s+, s- & s? states
            // therefore there maynot be an action that exits the given MEC
            break;
          }
          choices = choices(currentState);
        }
        else {
          nextActionIndex = sampleNextAction(currentState);
        }

        assert nextActionIndex != -1;

        // If the sampled action's index is the last index and state is a part of an mec, then this index of a stay action.
        // Here, we simply sample the next state. However, if we don't have a stay action, we have to call the explorer to
        // sample the next state according to the real distributions.
        if (!runAsReachChecker && nextActionIndex == choices.size() - 1 && stateToMecMap.containsKey(currentState)) {
          nextState = choices.get(nextActionIndex).sample();
          stayActionCounts.put(stateToMecMap.get(currentState), stayActionCounts.get(stateToMecMap.get(currentState))+1);
        }
        else {
          nextState = explorer.simulateAction(currentState, nextActionIndex);
          // If this action has been sampled enough number of times, we know that it can now be considered as a part of an MEC.
          // Hence, we know that there might be new MECs in the model and it could be worthwhile finding them again.
          seenNewTransitionSignificantly |= explorer.updateCounts(currentState, nextActionIndex, nextState);

          // Update transition-confidence state for methods that maintain their own estimates.
          if (transitionProbabilityMethod == TransitionProbabilityMethod.Martingale) {
            updateMartingaleTransitions(currentState, nextActionIndex, nextState);
          } else if (transitionProbabilityMethod == TransitionProbabilityMethod.Bernstein) {
            updateBernsteinTransitions(currentState, nextActionIndex, nextState);
          }
        }
        totalTransitionsSimulated++;

        // This is true when the currentState doesn't have any choices from it, i.e. it is a sink state.
        if (nextState == -1) {
          break;
        }
        currentState = nextState;
      }
    }

    handleComponents();

    if (transitionProbabilityMethod == TransitionProbabilityMethod.Hoeffding) {
      // we reset the bounds because we are dynamically changing transDelta
      values.resetBounds();
      if (!runAsReachChecker) {
        initSinkStates();
      }
    }

    // the update function is ran until there has been some progress, i.e., the upper bounds of some state have been changed.
    // if there has been change, this change needs to be propagated through the rest of the states.
    boolean ifProgress = true;
    int nMaxUpdates = explorer.exploredStateCount();
    int nUpdates = 0;
    while(ifProgress && nUpdates < nMaxUpdates) {
      ifProgress = update();
      nUpdates++;
    }

    return true;

  }

  private void computeDeltaT(BlackExplorer<S, M> explorer, double errorTolerance, int run) {
    switch (deltaTCalculationMethod) {
      case P_MIN:
        transDelta = errorTolerance *pMin/ explorer.getNumExploredActions();
        break;

      case MAX_SUCCESSORS:
        transDelta = errorTolerance / (explorer.getNumExploredActions() * maxSuccessorsInModel);
        break;
    }
    double series = (6 / Math.pow(Math.PI, 2)) * (1 / Math.pow(run, 2));
    transDelta = transDelta * series;
    explorer.updateCountParams(transDelta, pMin);
  }

  private Pair<Integer, Integer> getSampledBestLeavingAction(int currentState) {
    BlackUnboundedReachValues values = (BlackUnboundedReachValues) this.values;
    Random randomIntegerSampler = new Random();

    int mecIndex = stateToMecMap.get(currentState);
    NatBitSet mecStates = this.mecs.get(mecIndex);
    List<Pair<Integer, Integer>> bestActionStatePairs = values.getBestLeavingAction(mecStates, this::choices);
    if (runAsReachChecker && bestActionStatePairs.isEmpty()) {
      return new Pair<Integer,Integer>(-1, -1);
    }
    int sampledActionIndex = randomIntegerSampler.nextInt(bestActionStatePairs.size());
    return bestActionStatePairs.get(sampledActionIndex);
  }

  private int sampleNextAction(int currentState) {
    BlackExplorer<S, M> explorer = (BlackExplorer<S, M>) this.explorer;

    int nextActionIndex = values.sampleNextAction(currentState, choices(currentState)); // index of the action from the state that is to be sampled next.
    // this happens when none of the actions look promising at all, i.e. all actions have a upper bound of 0.
    // To continue the simulation, we forcefully sample an action.
    if (nextActionIndex == -1) {
      nextActionIndex = explorer.sampleNextAction(currentState);
    }

    return nextActionIndex;
  }

  /**
   * Updates the bounds of the model according to the latest changes.
   * @return true, if there have been any changes to the bounds of the states, else false.
   */
  private boolean update(){
    BlackUnboundedReachValues values = (BlackUnboundedReachValues) this.values;
    values.cacheCurrBounds(); // cache the current bounds to check if we will make any progress in update.

    for (int state: explorer.exploredStates()){
      List<Distribution> realChoices = choices(state);
      values.update(state, realChoices);
    }

    for (NatBitSet mec : this.mecs) {
      values.deflate(mec, this::choices);
    }

    return values.checkProgress();
  }

  /**
   * @param mecIndex: index of MEC for which bounds reward bounds are desired.
   * @return Reward bounds of the MEC in question.
   */
  @Override
  protected Bounds getMecBounds(int mecIndex) {
    return BoundedMecQuotient.getBoundsFromStayAction(stayActionMap.get(mecIndex));
  }

  /**
   * @param mecIndex: index of the desired MEC.
   * @return MEC object for the desired mecRepresentative.
   */
  @Override
  protected Mec getMec(int mecIndex) {

    NatBitSet mecStates = mecs.get(mecIndex);

    return Mec.create(explorer().model(), mecStates);
  }

  /**
   * Updates stay action of the MEC according to the given scaled bounds.
   * @param mecIndex: index of the desired MEC.
   * @param scaledBounds: scaled reward bounds for the MEC according to which the stay action is to be updated.
   */
  @Override
  protected void updateStayAction(int mecIndex, Bounds scaledBounds) {
    Distribution stayAction = BoundedMecQuotient.getStayDistribution(scaledBounds);
    stayActionMap.put(mecIndex, stayAction);
  }

  /**
   * Implements lines 11-15 in CAV'17 paper. Runs VI on mec.
   * @param mecIndex: Index of mec on which VI has to be run.
   */
  @Override
  protected void updateMec(int mecIndex){

    BlackExplorer<S, M> explorer = (BlackExplorer<S, M>) this.explorer;

    // mecBounds now contain the scaled reward upper and lower bounds.
    Bounds mecBounds = getMecBounds(mecIndex);

    double currPrecision = mecBounds.difference()*this.rMax;

    if(currPrecision<this.precision/2){
      return;
    }

    double targetPrecision = currPrecision/2;

    // get all the MEC states corresponding to mecRepresentative.
    Mec mec = getMec(mecIndex);

    if (mec.states.size()==0){
      return;
    }

    // We start with 1, because if 0, the requiredSamples become NaN
    int nTransitions = 1;
    for(int state: mec.actions.keySet()) {
      for(int actionInd: mec.actions.get(state)) {
        nTransitions += explorer.model().getChoice(state, actionInd).size();
      }
    }

    simulateMec(explorer, mec, nTransitions, computeNSamples(mec));

    assert !isZero(targetPrecision);

    // lambda function that returns a state object when given the state index. required for accessing reward generator function.
    Int2ObjectFunction<S> stateIndexMap = explorer::getState;

    RestrictedMecBoundedValueIterator<S> valueIterator = new RestrictedMecBoundedValueIterator<>(mec, targetPrecision/2,
            rewardGenerator, stateIndexMap, rMax, timeout);

    valueIterator.setDistributionFunction(x -> y -> this.explorer.model().getChoice(x, y));
    valueIterator.setLabelFunction(x -> y -> this.explorer.model().getActions(x).get(y).label());

    valueIterator.run();

    Bounds newBounds = valueIterator.getBounds();
    Bounds scaledBounds = Bounds.of(newBounds.lowerBound()/this.rMax, newBounds.upperBound()/this.rMax);

    // In the case when we run VI after some new states have been added, the lower bounds may be worse than the
    // previously computed bounds. However, we know that the MEC's reward must be greater than the previously computed
    // lower bound value. Thus, we can use the previously computer lower bound value for slightly faster convergence.
    scaledBounds = scaledBounds.withLower(Math.max(scaledBounds.lowerBound(), mecBounds.lowerBound()));

    if (!runAsReachChecker) {
      updateStayAction(mecIndex, scaledBounds);
    }
  }

  private double computeNSamples(Mec mec) {
    BlackExplorer<S, M> explorer = (BlackExplorer<S, M>) this.explorer;
    Pair<Integer, Integer> pair = explorer.getLeastVisitedStateAction(mec);
    double currentCount = explorer.getActionCounts(pair.first, pair.second);

    return getNextNSamples(currentCount);
  }

  private double getNextNSamples(double currentCount) {
    double nSamples = initialNSamples;
    while (nSamples < currentCount) {
      nSamples = nSamples * multiplicativeFactor;

      if (nSamples > 1e8) {
        nSamples = 1e8;
        break;
      }
    }

    return nSamples;
  }

  private void simulateMec(BlackExplorer<S, M> explorer, Mec mec, int nTransitions, double requiredSamples) {
    switch (simulateMec) {
      case STANDARD: explorer.simulateMECRepeatedly3(mec, requiredSamples, nTransitions);
      break;

      case CHEAT: explorer.simulateMECRepeatedly1(mec, requiredSamples);
      break;

      case HEURISTIC: explorer.simulateMECRepeatedly2(mec, requiredSamples, nTransitions);
      break;
    }
  }

  protected boolean shouldHandleComponents() {
    return seenNewTransitionSignificantly;
  }

  protected void resetSeenTransitionsSignificantlyFlag() {
    seenNewTransitionSignificantly = false;
  }

  @Override
  public void handleComponents(){

    // if no new transition has been seen significantly, don't compute mecs.
    if(!shouldHandleComponents()){
      return;
    }

    resetSeenTransitionsSignificantlyFlag();

    BlackExplorer<S, M> explorer = (BlackExplorer<S, M>) explorer();
    BlackUnboundedReachValues values = (BlackUnboundedReachValues) this.values;

    NatBitSet states = NatBitSets.copyOf(explorer.exploredStates());

    // activate the action count filter. Now explorer.model() only contains those actions that have been sampled
    // requiredSamples number of times. (Refer to Algorithm 3 in CAV'19). Now we can get a delta-sure EC.
    explorer.activateActionCountFilter();
    List<NatBitSet> newComponents = mecAnalyser.findComponents(explorer.model(), states);  // find all MECs in the partial model.

    // if no new components have been found, we clear all mec info that has been computed until now.
    if(newComponents.isEmpty()){
      this.mecs.clear();
      this.stateToMecMap.clear();
      this.stayActionMap.clear();
      this.mecValueCache.clear();
      // deactivate action count filter so that the original actions are restored in the model.
      explorer.deactivateActionCountFilter();
      return;
    }

    // udpates all the mec information variables according to the latest computations.
    NatBitSet changedMecs = updateMecInfo(newComponents);

    if (changedMecs.isEmpty()) {
      explorer.deactivateActionCountFilter();
      return;
    }

    // This deflates the values of the states of the new mecs. Further, the stay action is added here.
    for(int i: changedMecs){
      // We need to run VI on the MEC again to account for the following case. It can be that the bounds on the MEC are
      // already very precise. Thus, the probability of reaching the uncertain state would be very small and we may
      // never be able to run VI on the newly added states again. Thus, we need to run VI straight after adding new
      // states.
      updateMec(i);
    }

    explorer.deactivateActionCountFilter();

    for(int i: changedMecs){
      NatBitSet newComponent = newComponents.get(i);

      values.deflate(newComponent, this::choices);
    }
  }

  /**
   * Updates mec information according to newComponents.
   * @param newComponents: List of sets where each set represents an mec.
   * @return the indices of new mecs.
   */
  private NatBitSet updateMecInfo(List<NatBitSet> newComponents){

    newComponents.sort((t1, t2) -> {
      if(t1.firstInt()<t2.firstInt()){
        return -1;
      }
      else if(t1.firstInt()>t2.firstInt()){
        return 1;
      }
      return 0;
    });

    int i=0;

    IntList unchangedMecs = new IntArrayList();
    IntList newIndices = new IntArrayList();

    for(NatBitSet newComponent: newComponents){
      int mecIndex = stateToMecMap.getOrDefault(newComponent.firstInt(), -1);
      boolean oldMec = true;
      for(int state: newComponent){
        int stateMecIndex = stateToMecMap.getOrDefault(state, -1);
        // if the stateMecIndex is -1, it means that it didn't have an mec index previously. Thus, it must be a new mec.
        if (stateMecIndex==-1) {
          oldMec = false;
        }
        if(stateMecIndex!=mecIndex){
          // if this state's previous mec index is not equal to the previous mec index of the previous states, and if the current
          // previous mecIndex is not -1 (if it were one, it could have meant that the current state was the first one),
          // then we are looking at a new mec.
          if (mecIndex!=-1){
            oldMec = false;
          }
          mecIndex = stateMecIndex;
        }
      }

      // If all the below conditions are satisfied, this mec must be unchanged.
      if (mecIndex != -1 && oldMec && mecs.get(mecIndex).size() == newComponent.size()){
        unchangedMecs.add(mecIndex);
        // this holds the new index of this mec according to the new computation.
        newIndices.add(i);
      }
      i++;
    }

    Int2ObjectMap<Distribution> newStayActionMap = new Int2ObjectOpenHashMap<>();
    Int2IntMap newStayActionCounts = new Int2IntOpenHashMap();

    // for unchanged mecs, we retain all mec info and put them at their new places.
    for(i=0; i<unchangedMecs.size(); i++){
      newStayActionMap.put(newIndices.getInt(i), stayActionMap.get(unchangedMecs.getInt(i)));
      newStayActionCounts.put(newIndices.getInt(i), stayActionCounts.get(unchangedMecs.getInt(i)));
    }

    stayActionMap = newStayActionMap;
    stayActionCounts = newStayActionCounts;
    this.mecs = newComponents;

    stateToMecMap.clear();

    for(i=0; i<this.mecs.size(); i++){
      for(int state: this.mecs.get(i)){
        stateToMecMap.put(state, i);
      }
    }

    // All indices that aren't newIndices (new indices of old mecs) are new mecs.
    NatBitSet newMecs = NatBitSets.ensureModifiable(NatBitSets.boundedFullSet(newComponents.size()));
    newMecs.andNot(newIndices);

    // stayActionMap.size() == oldMecs.size()
    assert stayActionMap.size() + newMecs.size() == mecs.size();

    // initializing info for new mecs.
    for(int mecIndex: newMecs){
      stayActionCounts.put(mecIndex, 0);
      stayActionMap.put(mecIndex, BoundedMecQuotient.getStayDistribution(Bounds.reachUnknown()));
    }

    return newMecs;

  }

  /**
   * The looping condition as found in algorithm 4 in CAV '19.
   * @param visitStack the list of states visited until now.
   * @return true if we are looping, else false
   */
  private boolean looping(IntList visitStack){

    // computes the set of mecs.
    handleComponents();
    // if the stateToMecMap consists of the last visited state, it indicates that the state is part of an mec and we are
    // probably looping.
    return stateToMecMap.containsKey(visitStack.getInt(visitStack.size()-1));
  }

  @Override
  protected List<Distribution> choices(int state) {
    assert explorer.isExploredState(state);
    assert !BoundedMecQuotient.isSinkState(state);

    List<Distribution> choices = new ArrayList<>(explorer.getChoices(state));
    if (!runAsReachChecker && stateToMecMap.containsKey(state)) {
      choices.add(stayActionMap.get(stateToMecMap.get(state)));
    }
    return choices;
  }

  @Override
  protected void onSamplingFinished(int initialState) {
    super.onSamplingFinished(initialState);

    if (calculateErrorProbability) {
      logger.log(Level.INFO, "Computing error probability");

      BlackExplorer<S, M> explorer = (BlackExplorer<S, M>) this.explorer;
      ErrorProbabilityCalculator errorProbabilityCalculator = new ErrorProbabilityCalculator(explorer::getActions,
              explorer.getOriginalStateActions(),
              explorer.getStateTransitionCounts(),
              stateToMecMap,
              mecs);
      double result = errorProbabilityCalculator.getErrorProbability(initialState);
      additionalWriteInfo.add(String.valueOf(result));
    }
  }
}
