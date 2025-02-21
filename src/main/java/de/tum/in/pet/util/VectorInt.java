package de.tum.in.pet.util;

import java.util.Arrays;

public class VectorInt {
    private final int[] elements;

    // Constructor
    public VectorInt(int... elements) {
        this.elements = elements;
    }

    // Static initializers
    public static VectorInt arange(int start, int stop, int step) {
        int size = (stop - start) / step;
        int[] values = new int[size];
        for (int i = 0; i < size; i++) {
            values[i] = start + i * step;
        }
        return new VectorInt(values);
    }

    public static VectorInt arange(int start, int stop) {
        return arange(start, stop, 1);
    }

    public static VectorInt zeros(int size) {
        return new VectorInt(new int[size]);
    }

    public static VectorInt ones(int size) {
        int[] onesArray = new int[size];
        Arrays.fill(onesArray, 1);
        return new VectorInt(onesArray);
    }

    public static VectorInt filled(int size, int value) {
        int[] filledArray = new int[size];
        Arrays.fill(filledArray, value);
        return new VectorInt(filledArray);
    }

    public static VectorInt append(VectorInt v, int scalar) {
        int[] newArray = Arrays.copyOf(v.elements, v.elements.length + 1);
        newArray[v.elements.length] = scalar;
        return new VectorInt(newArray);
    }

    public static VectorInt prepend(VectorInt v, int scalar) {
        int[] newArray = new int[v.elements.length + 1];
        newArray[0] = scalar;
        System.arraycopy(v.elements, 0, newArray, 1, v.elements.length);
        return new VectorInt(newArray);
    }

    public static VectorInt copy(VectorInt v) {
        return new VectorInt(Arrays.copyOf(v.elements, v.elements.length));
    }

    // Returns the size of the vector
    public int size() {
        return elements.length;
    }

    // Indexing method
    public static int index(VectorInt v, int idx) {
        if (idx < 0 || idx >= v.elements.length) {
            throw new IndexOutOfBoundsException("Index out of bounds");
        }
        return v.elements[idx];
    }

    // Slicing method
    public static VectorInt slice(VectorInt v, int start, int end) {
        if (start < 0 || end > v.elements.length || start > end) {
            throw new IllegalArgumentException("Invalid slice range");
        }
        return new VectorInt(Arrays.copyOfRange(v.elements, start, end));
    }

    // Replace occurrences of a value
    public static VectorInt replace(VectorInt v, int from, int to) {
        int[] result = Arrays.copyOf(v.elements, v.elements.length);
        for (int i = 0; i < result.length; i++) {
            if (result[i] == from) {
                result[i] = to;
            }
        }
        return new VectorInt(result);
    }

    // Replace values in v where mask is not 0
    public static VectorInt replace(VectorInt v, VectorInt mask, int value) {
        if (v.elements.length != mask.elements.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        int[] result = Arrays.copyOf(v.elements, v.elements.length);
        for (int i = 0; i < result.length; i++) {
            if (mask.elements[i] != 0) {
                result[i] = value;
            }
        }
        return new VectorInt(result);
    }

    // Sum of elements
    public static int sum(VectorInt v) {
        return Arrays.stream(v.elements).sum();
    }

    // Product of elements
    public static int product(VectorInt v) {
        return Arrays.stream(v.elements).reduce(1, (a, b) -> a * b);
    }

    // Cumulative sum of elements
    public static VectorInt cumulativeSum(VectorInt v) {
        int[] result = new int[v.elements.length];
        int sum = 0;
        for (int i = 0; i < v.elements.length; i++) {
            sum += v.elements[i];
            result[i] = sum;
        }
        return new VectorInt(result);
    }

    // Cumulative product of elements
    public static VectorInt cumulativeProduct(VectorInt v) {
        int[] result = new int[v.elements.length];
        int product = 1;
        for (int i = 0; i < v.elements.length; i++) {
            product *= v.elements[i];
            result[i] = product;
        }
        return new VectorInt(result);
    }

    // Replace the add, subtract, multiply, divide, power methods with:
    public static VectorInt add(VectorInt v1, VectorInt v2) {
        if (v1.elements.length != v2.elements.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = v1.elements[i] + v2.elements[i];
        }
        return new VectorInt(result);
    }

    public static VectorInt subtract(VectorInt v1, VectorInt v2) {
        if (v1.elements.length != v2.elements.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = v1.elements[i] - v2.elements[i];
        }
        return new VectorInt(result);
    }

    public static VectorInt multiply(VectorInt v1, VectorInt v2) {
        if (v1.elements.length != v2.elements.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = v1.elements[i] * v2.elements[i];
        }
        return new VectorInt(result);
    }

    public static VectorInt divide(VectorInt v1, VectorInt v2) {
        if (v1.elements.length != v2.elements.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = v2.elements[i] != 0 ? v1.elements[i] / v2.elements[i] : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt power(VectorInt v1, VectorInt v2) {
        if (v1.elements.length != v2.elements.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = (int) Math.pow(v1.elements[i], v2.elements[i]);
        }
        return new VectorInt(result);
    }

    // Similarly update min/max methods:
    public static VectorInt min(VectorInt v1, VectorInt v2) {
        if (v1.elements.length != v2.elements.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = Math.min(v1.elements[i], v2.elements[i]);
        }
        return new VectorInt(result);
    }

    public static VectorInt max(VectorInt v1, VectorInt v2) {
        if (v1.elements.length != v2.elements.length) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = Math.max(v1.elements[i], v2.elements[i]);
        }
        return new VectorInt(result);
    }

    // Replace scalar operation methods
    public static VectorInt min(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = Math.min(v.elements[i], scalar);
        }
        return new VectorInt(result);
    }

    public static VectorInt max(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = Math.max(v.elements[i], scalar);
        }
        return new VectorInt(result);
    }

    public static VectorInt add(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = v.elements[i] + scalar;
        }
        return new VectorInt(result);
    }

    public static VectorInt subtract(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = v.elements[i] - scalar;
        }
        return new VectorInt(result);
    }

    public static VectorInt multiply(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = v.elements[i] * scalar;
        }
        return new VectorInt(result);
    }

    public static VectorInt divide(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = scalar != 0 ? v.elements[i] / scalar : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt power(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = (int) Math.pow(v.elements[i], scalar);
        }
        return new VectorInt(result);
    }

    // Scalar divided by vector (1/x elementwise)
    public static VectorInt divide(int scalar, VectorInt v) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = v.elements[i] != 0 ? scalar / v.elements[i] : 0;
        }
        return new VectorInt(result);
    }

    // Scalar minus vector (scalar - each element)
    public static VectorInt subtract(int scalar, VectorInt v) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = scalar - v.elements[i];
        }
        return new VectorInt(result);
    }

    // Elementwise square root
    public static VectorInt sqrt(VectorInt v) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = (int) Math.sqrt(v.elements[i]);
        }
        return new VectorInt(result);
    }

    // Elementwise natural logarithm
    public static VectorInt log(VectorInt v) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = (int) Math.log(v.elements[i]);
        }
        return new VectorInt(result);
    }

    // Elementwise comparison operations
    public static VectorInt equals(VectorInt v1, VectorInt v2) {
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = v1.elements[i] == v2.elements[i] ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt notEquals(VectorInt v1, VectorInt v2) {
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = v1.elements[i] != v2.elements[i] ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt lessThan(VectorInt v1, VectorInt v2) {
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = v1.elements[i] < v2.elements[i] ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt lessThanOrEqual(VectorInt v1, VectorInt v2) {
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = v1.elements[i] <= v2.elements[i] ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt greaterThan(VectorInt v1, VectorInt v2) {
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = v1.elements[i] > v2.elements[i] ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt greaterThanOrEqual(VectorInt v1, VectorInt v2) {
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = v1.elements[i] >= v2.elements[i] ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt equals(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = v.elements[i] == scalar ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt notEquals(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = v.elements[i] != scalar ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt lessThan(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = v.elements[i] < scalar ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt lessThanOrEqual(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = v.elements[i] <= scalar ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt greaterThan(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = v.elements[i] > scalar ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt greaterThanOrEqual(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = v.elements[i] >= scalar ? 1 : 0;
        }
        return new VectorInt(result);
    }

    // Elementwise logical operations
    public static VectorInt and(VectorInt v1, VectorInt v2) {
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = (v1.elements[i] != 0 && v2.elements[i] != 0) ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt or(VectorInt v1, VectorInt v2) {
        int[] result = new int[v1.elements.length];
        for (int i = 0; i < v1.elements.length; i++) {
            result[i] = (v1.elements[i] != 0 || v2.elements[i] != 0) ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt not(VectorInt v) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = v.elements[i] == 0 ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt and(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = (v.elements[i] != 0 && scalar != 0) ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static VectorInt or(VectorInt v, int scalar) {
        int[] result = new int[v.elements.length];
        for (int i = 0; i < v.elements.length; i++) {
            result[i] = (v.elements[i] != 0 || scalar != 0) ? 1 : 0;
        }
        return new VectorInt(result);
    }

    public static boolean all(VectorInt v) {
        for (int e : v.elements) {
            if (e == 0) {
                return false;
            }
        }
        return true;
    }

    // Check if at least one value is non-zero
    public static boolean any(VectorInt v) {
        for (int e : v.elements) {
            if (e != 0) {
                return true;
            }
        }
        return false;
    }

    // Helper method for elementwise operations between two vectors
    // ToString method for easy display
    @Override
    public String toString() {
        return Arrays.toString(elements);
    }
}
