package de.tum.in.pet.sampler;

import de.tum.in.naturals.set.NatBitSet;
import de.tum.in.naturals.set.NatBitSets;
import de.tum.in.pet.util.ModelHelper;
import de.tum.in.pet.util.SampleUtil;
import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.ValueVerdict;
import de.tum.in.pet.values.unbounded.StateUpdate;
import de.tum.in.pet.values.unbounded.StateValues;
import de.tum.in.probmodels.explorer.Explorer;
import de.tum.in.probmodels.graph.ComponentAnalyser;
import de.tum.in.probmodels.graph.SccDecomposition;
import de.tum.in.probmodels.model.CollapseModel;
import de.tum.in.probmodels.model.CollapseView;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.Model;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntStack;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;
import java.util.function.ToDoubleFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import prism.PrismException;

@SuppressWarnings("PMD.TooManyFields")
public class UnboundedSampler<S, M extends Model> implements Sampler<S, M> {
  // TODO: Currently getDifference(unexplored state) always returns [0, 1] - intended?

  private static final Logger logger = Logger.getLogger(UnboundedSampler.class.getName());

  private final Explorer<S, M> explorer;
  private final CollapseModel<M> collapseModel;
  private final StateUpdate stateUpdate;
  private final ValueVerdict verdict;

  private final ComponentAnalyser analyser;

  private final SuccessorHeuristic heuristic;
  private final StateValues stateValues;

  private boolean newStatesSinceCollapse = true;
  private long collapseThreshold;
  private long loopCount = 0;

  private long sampleSteps = 0;
  private long samples = 0;
  private long backtraceCount = 0;
  private long backtraceSteps = 0;

  private final NatBitSet statesInComponents = NatBitSets.set();
  private final NatBitSet sampledStates = NatBitSets.set();

  private final long reportProgressEverySteps;
  private final int maxBacktrackPerSample;
  private final int maxExploresPerSample;
  private final CollapseMethod collapseMethod;

  public UnboundedSampler(Explorer<S, M> explorer, StateValues stateValues,
      SuccessorHeuristic heuristic,
      StateUpdate stateUpdate, ValueVerdict verdict, ComponentAnalyser analyser,
      UnboundedSamplerConfig config) {
    this.explorer = explorer;
    this.stateValues = stateValues;
    this.heuristic = heuristic;
    this.collapseModel = new CollapseView<>(explorer.model());
    this.stateUpdate = stateUpdate;
    this.verdict = verdict;
    this.analyser = analyser;
    this.collapseThreshold = config.initialCollapseThreshold();

    maxBacktrackPerSample = config.maxBacktrackPerSample();
    maxExploresPerSample = config.maxExploresPerSample();
    reportProgressEverySteps = config.reportProgressEverySteps();
    collapseMethod = config.collapseMethod();
  }

  @Override
  public Explorer<S, M> explorer() {
    return explorer;
  }

  @Override
  public AnnotatedModel<M> model() {
    return new AnnotatedModel<>(explorer.model(), explorer::getState,
        NatBitSets.copyOf(explorer.exploredStates()));
  }

  @Override
  public Bounds bounds(int state) {
    return stateValues.bounds(state);
  }

  @Override
  public void run() throws PrismException {
    long timer = System.nanoTime();

    for (int initialState : explorer.initialStates()) {
      // The representative of the initial states might be a different state
      int representative = collapseModel.representative(initialState);
      while (!isSolved(representative)) {
        if (sample(representative)) {
          // If MECs have been merged, update the representative (it might have changed)
          representative = collapseModel.representative(initialState);
          assert collapseModel.representative(representative) == collapseModel
              .representative(initialState) && explorer
              .isExploredState(representative);
        }
      }
    }

    // writeDotModel("test.dot", null);

    long elapsedTime = System.nanoTime() - timer;

    logger.log(Level.INFO, () -> String.format("%n== Finished sampling ==%n%s%n  Time: %f sec%n",
        getProgressString(), elapsedTime / (double) TimeUnit.SECONDS.toNanos(1)));
  }

  private boolean isSolved(int state) {
    return verdict.isSolved(stateValues.bounds(state));
  }

  private boolean sample(int initialState) throws PrismException {
    assert !isSolved(initialState);

    samples += 1;

    IntList visitedStates = new IntArrayList();
    IntStack visitStack = (IntStack) visitedStates;
    IntSet visitedStateSet = NatBitSets.set();

    int currentState = initialState;
    int exploreCount = 0;
    int sampleBacktraceCount = 0;
    // Sample a path
    while (!visitedStateSet.contains(currentState)) {
      assert explorer.isExploredState(currentState);

      sampleSteps++;
      if (logger.isLoggable(Level.FINE) && sampleSteps % reportProgressEverySteps == 0) {
        logger.log(Level.FINE, String.format("%n== Progress ==%n%s", getProgressString()));
      }

      visitStack.push(currentState);
      visitedStateSet.add(currentState);
      // Sample the successor
      int nextState = sampleNextState(currentState);

      if (nextState == -1 || currentState == nextState) {
        if (sampleBacktraceCount == maxBacktrackPerSample) {
          break;
        }

        // We won't find anything of value if we continue to follow this path, backtrace until we
        // find an interesting state again
        // Note: We might as well completely restart the sampling here, but then we potentially
        // have to move to this interesting "fringe" region again
        do {
          backtraceSteps += 1;
          currentState = visitStack.popInt();
          visitedStateSet.remove(currentState);
          update(currentState);
        } while (isSolved(currentState) && currentState != initialState);
        if (currentState == initialState) {
          break;
        }
        assert !isSolved(currentState);

        backtraceCount += 1;
        sampleBacktraceCount += 1;
      } else {
        if (!explorer.isExploredState(nextState)) {
          if (exploreCount == maxExploresPerSample) {
            break;
          }
          exploreCount += 1;
          explore(nextState);
        }

        currentState = nextState;
      }
    }
    assert visitedStateSet.equals(NatBitSets.copyOf(visitedStates));
    sampledStates.or(visitedStateSet);

    // Handle end components
    if (visitedStateSet.contains(currentState)) {
      // Sampled the same state twice
      assert explorer.isExploredState(currentState);
      loopCount += 1;
      // We looped quite often - chances for this are high if there is a MEC, otherwise the
      // sampling probabilities would decrease
      if (loopCount > collapseThreshold) {
        // Search for fix points in the model
        // TODO Only search on frequently visited states?
        boolean anythingChanged = handleComponents();

        loopCount = 0;
        // Some arbitrary increasing number - collapsing is expensive
        collapseThreshold += explorer.exploredStates().size();

        if (anythingChanged) {
          // The path could now contain invalid states (i.e. some which have been merged), we have
          // to sample again.
          visitedStates.clear();
          return true;
        }
      }
    }

    // Propagate values backwards along the path
    while (!visitStack.isEmpty()) {
      update(visitStack.popInt());
    }

    return false;
  }

  private int sampleNextState(int state) throws PrismException {
    assert explorer.isExploredState(state);
    assert !collapseModel.isRemoved(state);

    List<Distribution> choices = collapseModel.getChoices(state);
    if (choices.isEmpty()) {
      update(state, choices);
      return -1;
    }

    // CSOFF: Indentation
    ToDoubleFunction<Distribution> selectionScore = stateUpdate.isSmallestFixPoint()
        ? d -> 1.0d - stateValues.lowerBound(state, d)
        : d -> stateValues.upperBound(state, d);
    return SampleUtil.sampleNextState(choices, heuristic, selectionScore, stateValues::difference,
        s -> s == state || verdict.isSolved(stateValues.bounds(s)));
    // CSON: Indentation
  }

  private void explore(int state) throws PrismException {
    assert !explorer.isExploredState(state);
    newStatesSinceCollapse = true;
    explorer.exploreState(state);
  }

  private Bounds update(int state) throws PrismException {
    return update(state, collapseModel.getChoices(state));
  }

  private Bounds update(int state, List<Distribution> choices) throws PrismException {
    assert explorer.isExploredState(state);
    assert !collapseModel.isRemoved(state);

    Bounds newValues = stateUpdate.update(state, choices, stateValues);
    stateValues.setBounds(state, newValues);
    return newValues;
  }

  private boolean handleComponents() throws PrismException {
    if (!newStatesSinceCollapse) {
      return false;
    }
    newStatesSinceCollapse = false;

    NatBitSet states;
    if (collapseMethod == CollapseMethod.ALL_STATES) {
      states = NatBitSets.copyOf(explorer.exploredStates());
      states.andNot(collapseModel.removedStates());
    } else {
      states = sampledStates;
    }
    assert !states.intersects(collapseModel.removedStates());

    List<NatBitSet> components = analyser.findComponents(collapseModel, states);
    sampledStates.clear();

    if (components.isEmpty()) {
      logger.log(Level.FINER, "Found no components");
      return false;
    }
    logger.log(Level.FINER, "Found components {0}", components);

    int statesInComponentsCount = statesInComponents.size();
    List<NatBitSet> newComponents = new ArrayList<>();
    for (NatBitSet component : components) {
      assert !component.isEmpty();

      // Check if any of the states in this component have not been touched yet - then it is new
      statesInComponents.or(component);
      int newSize = statesInComponents.size();
      if (newSize > statesInComponentsCount) {
        statesInComponentsCount = newSize;
        newComponents.add(component);
      }
    }

    if (logger.isLoggable(Level.FINE)) {
      int count = newComponents.stream().mapToInt(NatBitSet::size).sum();
      logger.log(Level.FINE, "Found {0} new components with {1} new states",
          new Object[] {newComponents.size(), count});
    }

    if (stateUpdate.isSmallestFixPoint()) {
      // TODO Hack - better?
      newComponents
          .removeIf(component -> !SccDecomposition.isBscc(collapseModel::getSuccessors, component));

      logger.log(Level.FINER, "Bottom components: {0}", newComponents);
      if (newComponents.isEmpty()) {
        return false;
      }
    } else {
      assert !newComponents.isEmpty();
    }

    IntList representatives = collapseModel.collapse(newComponents);
    Iterator<NatBitSet> collapseIterator = newComponents.iterator();
    IntIterator representativeIterator = representatives.iterator();

    while (collapseIterator.hasNext()) {
      NatBitSet collapseStates = collapseIterator.next();
      int representative = representativeIterator.nextInt();

      assert explorer.isExploredState(representative);
      // Delete information of all collapsed states
      collapseStates.forEach((int s) -> {
        if (s != representative) {
          stateValues.clear(s);
        }
      });
      // Update
      stateUpdate.updateCollapsed(representative, collapseModel.getChoices(representative),
          collapseStates, stateValues);
    }

    return true;
  }

  private String getProgressString() {
    Int2ObjectMap<Bounds> initialStateValues = new Int2ObjectAVLTreeMap<>();
    explorer.initialStates().forEach((int initialState) -> {
      int collapsedRepresentative = collapseModel.representative(initialState);
      initialStateValues.put(initialState, stateValues.bounds(collapsedRepresentative));
    });
    // writeDotModel("test.dot", null);

    return String.format("  Trials: %d, steps: %d, avg len: %f, backtrace count: %d, steps: %d%n"
            + "  States: %d/%d in partial/collapsed model%n"
            + "  Bounds: %s",
        samples, sampleSteps, (double) sampleSteps / (double) samples, backtraceCount,
        backtraceSteps, explorer.exploredStateCount(),
        explorer.exploredStateCount() - collapseModel.removedStates().size(),
        initialStateValues);
  }

  @SuppressWarnings("PMD.UnusedPrivateMethod")
  private void writeDotModel(String filename, @Nullable IntPredicate highlight) {
    logger.log(Level.WARNING, "Writing model as dot file!");
    IntPredicate nonRemovedStates = s -> !collapseModel.isRemoved(s);
    ModelHelper.modelWithBoundsToDotFile(filename, collapseModel, explorer, stateValues,
        nonRemovedStates, highlight);
  }
}
