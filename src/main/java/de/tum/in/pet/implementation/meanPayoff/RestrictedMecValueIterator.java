package de.tum.in.pet.implementation.meanPayoff;

import de.tum.in.pet.values.Bounds;
import de.tum.in.probmodels.generator.RewardGenerator;
import de.tum.in.probmodels.model.Action;
import de.tum.in.probmodels.model.Distribution;
import de.tum.in.probmodels.model.Model;
import de.tum.in.probmodels.model.RestrictedModel;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntCollection;
import parser.State;

import java.util.List;

public class RestrictedMecValueIterator<M extends Model> {

  public final RestrictedModel<M> restrictedModel;
  public final double targetPrecision;
  public final Int2DoubleOpenHashMap values;
  public final RewardGenerator<State> rewardGenerator;

  public RestrictedMecValueIterator(RestrictedModel<M> restrictedModel, double targetPrecision, RewardGenerator<State> rewardGenerator){
    this.restrictedModel = restrictedModel;
    this.targetPrecision = targetPrecision;
    this.values = new Int2DoubleOpenHashMap();
    this.rewardGenerator = rewardGenerator;
  }

  public RestrictedMecValueIterator(RestrictedModel<M> restrictedModel, double targetPrecision, RewardGenerator<State> rewardGenerator,
                                    Int2DoubleOpenHashMap values){
    this.restrictedModel = restrictedModel;
    this.targetPrecision = targetPrecision;
    this.values = values;
    this.rewardGenerator = rewardGenerator;

  }

  public void run(){
    // IntCollection initialStates = restrictedModel.model().getInitialStates();
    // int[] initialStates_arr = initialStates.toIntArray();
    // assert !(initialStates_arr.length == 0);
    // int currentState = initialStates_arr[0];
    // Get choices from currentState in restrictedModel
    // restrictedModel.model() gives the corresponding MarkovDecisionProcess object.

    int numStates = restrictedModel.model().getNumStates();

    for (int state = 0; state < numStates; state++) {
      Bounds max_bound_action_value = Bounds.of(0.0, 0.0);
      List<Action> actions = restrictedModel.model().getActions(state);
      for (Action action : actions) {
        Bounds bounds = get_action_bounds(state, action.distribution());

      }

    }
  }

  private Bounds get_action_bounds(int state, Distribution distribution) {
    int numSuccessors = distribution.size();
    for (int successor = 0; successor < numSuccessors; successor++) {
      Bounds bounds = bounds(successor);

    }



    double lower = 0.0d;
    double upper = 0.0d;
    double sum = 0.0d;
    for (Int2DoubleMap.Entry entry : distribution) {
      int successor = entry.getIntKey();
      if (successor == state) {
        continue;
      }
      Bounds successorBounds = bounds(successor);
      double probability = entry.getDoubleValue();
      sum += probability;
      lower += successorBounds.lowerBound() * probability;
      upper += successorBounds.upperBound() * probability;
    }
    if (sum == 0.0d) {
      return bounds(state);
    }
    return Bounds.reach(lower / sum, upper / sum);
  }

  @Override
  public Bounds bounds(int state) {
    return target.test(state)
            ? Bounds.reachOne()
            : bounds.getOrDefault(state, Bounds.reachUnknown());
  }


  public Bounds getBounds(){
    return null;
  }

  public Int2DoubleOpenHashMap getValues(){
    return this.values;
  }
}
