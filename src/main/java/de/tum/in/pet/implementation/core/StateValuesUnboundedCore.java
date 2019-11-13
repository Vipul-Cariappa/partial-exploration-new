package de.tum.in.pet.implementation.core;

import static com.google.common.base.Preconditions.checkArgument;
import static de.tum.in.probmodels.util.Util.isOne;
import static de.tum.in.probmodels.util.Util.isZero;
import static de.tum.in.probmodels.util.Util.lessOrEqual;

import de.tum.in.pet.values.Bounds;
import de.tum.in.pet.values.unbounded.StateValues;
import de.tum.in.probmodels.model.Distribution;
import it.unimi.dsi.fastutil.ints.Int2DoubleLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

public class StateValuesUnboundedCore implements StateValues {
  private final Int2DoubleMap bounds = new Int2DoubleLinkedOpenHashMap();
  private final IntSet zeroStates = new IntOpenHashSet();

  public StateValuesUnboundedCore() {
    bounds.defaultReturnValue(1.0d);
  }

  @Override
  public Bounds bounds(int state) {
    return Bounds.reach(0.0d, upperBound(state));
  }

  @Override
  public double upperBound(int state) {
    if (zeroStates.contains(state)) {
      return 0.0d;
    }
    return bounds.getOrDefault(state, 1.0d);
  }

  @Override
  public double upperBound(int state, Distribution distribution) {
    return distribution.sumWeightedExcept(this::upperBound, state);
  }

  @Override
  public double lowerBound(int state) {
    return 0.0d;
  }

  @Override
  public double difference(int state) {
    return upperBound(state);
  }

  @Override
  public boolean isZeroDifference(int state) {
    return zeroStates.contains(state);
  }

  @Override
  public void setUpperBound(int state, double value) {
    assert 0 <= value && value <= 1.0d : "Value " + String.format("%.6g", value)
        + " not within bounds";
    if (isOne(value)) {
      assert !bounds.containsKey(state);
      return;
    }
    if (isZero(value)) {
      setZero(state);
      return;
    }

    double oldValue = bounds.put(state, value);
    assert lessOrEqual(value, oldValue) :
        "Value " + String.format("%.6g", value) + " larger than old value " + String
            .format("%.6g", oldValue);
  }

  @Override
  public void setLowerBound(int state, double value) {
    checkArgument(value == 0.0d, "Non-zero lower bound %s", value);
  }

  @Override
  public void setBounds(int state, double lowerBound, double upperBound) {
    checkArgument(lowerBound == 0.0d, "Non-zero lower bound %s", lowerBound);
    setUpperBound(state, upperBound);
  }

  public void setZero(int state) {
    bounds.remove(state);
    zeroStates.add(state);
    assert upperBound(state) == 0.0d;
  }

  @Override
  public void clear(int state) {
    zeroStates.remove(state);
    bounds.remove(state);
    //noinspection FloatingPointEquality
    assert upperBound(state) == 1.0d;
  }
}
