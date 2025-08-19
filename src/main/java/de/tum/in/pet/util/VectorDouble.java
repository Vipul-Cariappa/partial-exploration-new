package de.tum.in.pet.util;

import java.util.Arrays;

public class VectorDouble {
    public double[] elements;
    public int size = 0;
    public int capacity = 0;

    // Constructor
    public VectorDouble(int size) {
        elements = new double[size];
        this.capacity = size;
    }

    public VectorDouble(int size, double value) {
        elements = new double[size];
        this.capacity = size;
        this.size = this.elements.length;
        Arrays.fill(this.elements, value);
    }

    public VectorDouble(double... elements) {
        this.elements = elements;
        size = this.elements.length;
        capacity = size;
    }

    public double at(int x) { return elements[x]; }

    public double back() { return elements[size - 1]; }

    // Static initializers
    public static VectorDouble arange(double start, double stop, double step) {
        int size = (int) Math.ceil((stop - start) / step);
        double[] values = new double[size];
        for (int i = 0; i < size; i++) {
            values[i] = start + i * step;
        }
        return new VectorDouble(values);
    }

    public static VectorDouble arange(double start, double stop) {
        return arange(start, stop, 1);
    }

    public static VectorDouble linspace(double start, double stop, int count) {
        if (count < 2) {
            throw new IllegalArgumentException("Count must be at least 2");
        }
        double step = (stop - start) / (count - 1);
        VectorDouble vector = new VectorDouble(new double[count]);
        for (int i = 0; i < count; i++) {
            vector.elements[i] = start + i * step;
        }
        return vector;
    }

    public static VectorDouble zeros(int size) {
        return new VectorDouble(new double[size]);
    }

    public static VectorDouble ones(int size) {
        double[] onesArray = new double[size];
        Arrays.fill(onesArray, 1.0);
        return new VectorDouble(onesArray);
    }

    public static VectorDouble filled(int size, double value) {
        double[] filledArray = new double[size];
        Arrays.fill(filledArray, value);
        return new VectorDouble(filledArray);
    }

    public void append(double scalar) {
        if (size >= capacity) {
            capacity = capacity * 2 + 1;
            elements = Arrays.copyOf(elements, capacity);
        }
        elements[size] = scalar;
        size++;
    }

    public static VectorDouble append(VectorDouble v, double scalar) {
        double[] newArray = Arrays.copyOf(v.elements, v.size + 1);
        newArray[v.size] = scalar;
        return new VectorDouble(newArray);
    }

    public static VectorDouble prepend(VectorDouble v, double scalar) {
        double[] newArray = new double[v.size + 1];
        newArray[0] = scalar;
        System.arraycopy(v.elements, 0, newArray, 1, v.size + 1);
        return new VectorDouble(newArray);
    }

    public static VectorDouble copy(VectorDouble v) {
        return new VectorDouble(Arrays.copyOf(v.elements, v.size));
    }

    // Returns the size of the vector
    public int size() {
        return size;
    }

    // Indexing method
    public static double index(VectorDouble v, int idx) {
        if (idx < 0 || idx >= v.size) {
            throw new IndexOutOfBoundsException("Index out of bounds");
        }
        return v.elements[idx];
    }

    // Set value at a specific index
    public static void set(VectorDouble v, int idx, double value) {
        if (idx < 0 || idx >= v.size) {
            throw new IndexOutOfBoundsException("Index out of bounds");
        }
        v.elements[idx] = value;
    }

    // Slicing method
    public static VectorDouble slice(VectorDouble v, int start, int end) {
        if (start < 0 || end > v.size || start > end) {
            throw new IllegalArgumentException("Invalid slice range");
        }
        return new VectorDouble(Arrays.copyOfRange(v.elements, start, end));
    }

    // Replace occurrences of a value
    public VectorDouble replace(double from, double to) {
        for (int i = 0; i < size; i++) {
            if (elements[i] == from) {
                elements[i] = to;
            }
        }
        return this;
    }

    public static VectorDouble replace(VectorDouble v, double from, double to) {
        double[] result = Arrays.copyOf(v.elements, v.size);
        for (int i = 0; i < result.length; i++) {
            if (result[i] == from) {
                result[i] = to;
            }
        }
        return new VectorDouble(result);
    }

    // Replace values in v where mask is not 0
    public static VectorDouble replace(VectorDouble v, VectorDouble mask, double value) {
        if (v.size != mask.size) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        double[] result = Arrays.copyOf(v.elements, v.size);
        for (int i = 0; i < result.length; i++) {
            if (mask.elements[i] != 0) {
                result[i] = value;
            }
        }
        return new VectorDouble(result);
    }

    // Sum of elements
    public static double sum(VectorDouble v) {
        return Arrays.stream(v.elements).sum();
    }

    // Product of elements
    public static double product(VectorDouble v) {
        return Arrays.stream(v.elements).reduce(1, (a, b) -> a * b);
    }

    // Cumulative sum of elements
    public static VectorDouble cumulativeSum(VectorDouble v) {
        double[] result = new double[v.size];
        double sum = 0;
        for (int i = 0; i < v.size; i++) {
            sum += v.elements[i];
            result[i] = sum;
        }
        return new VectorDouble(result);
    }

    // Cumulative product of elements
    public VectorDouble cumulativeProduct() {
        double product = 1;
        for (int i = 0; i < size; i++) {
            product *= elements[i];
            elements[i] = product;
        }
        return this;
    }

    public static VectorDouble cumulativeProduct(VectorDouble v) {
        double[] result = new double[v.size];
        double product = 1;
        for (int i = 0; i < v.size; i++) {
            product *= v.elements[i];
            result[i] = product;
        }
        return new VectorDouble(result);
    }

    /**
     * Computes the running maximum of elements
     *
     * @param v Input vector
     * @return Vector containing running maximum values
     */
    public static VectorDouble maximumAccumulate(VectorDouble v) {
        double[] result = new double[v.size];
        if (v.size > 0) {
            result[0] = v.elements[0];
            for (int i = 1; i < v.size; i++) {
                result[i] = Math.max(result[i - 1], v.elements[i]);
            }
        }
        return new VectorDouble(result);
    }

    /**
     * Computes the running minimum of elements
     *
     * @param v Input vector
     * @return Vector containing running minimum values
     */
    public static VectorDouble minimumAccumulate(VectorDouble v) {
        double[] result = new double[v.size];
        if (v.size > 0) {
            result[0] = v.elements[0];
            for (int i = 1; i < v.size; i++) {
                result[i] = Math.min(result[i - 1], v.elements[i]);
            }
        }
        return new VectorDouble(result);
    }

    // Replace the add, subtract, multiply, divide, power methods with:
    public static VectorDouble add(VectorDouble v1, VectorDouble v2) {
        if (v1.size != v2.size) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = v1.elements[i] + v2.elements[i];
        }
        return new VectorDouble(result);
    }

    public VectorDouble subtract(VectorDouble v2) {
        if (size != v2.size) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        for (int i = 0; i < size; i++) {
            elements[i] = elements[i] - v2.elements[i];
        }
        return this;
    }

    public static VectorDouble subtract(VectorDouble v1, VectorDouble v2) {
        if (v1.size != v2.size) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = v1.elements[i] - v2.elements[i];
        }
        return new VectorDouble(result);
    }

    public VectorDouble multiply(VectorDouble v2) {
        if (size != v2.size) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        for (int i = 0; i < size; i++) {
            elements[i] = elements[i] * v2.elements[i];
        }
        return this;
    }

    public static VectorDouble multiply(VectorDouble v1, VectorDouble v2) {
        if (v1.size != v2.size) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = v1.elements[i] * v2.elements[i];
        }
        return new VectorDouble(result);
    }

    public static VectorDouble divide(VectorDouble v1, VectorDouble v2) {
        if (v1.size != v2.size) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = v2.elements[i] != 0 ? v1.elements[i] / v2.elements[i] : (v2.elements[i] >= 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
        }
        return new VectorDouble(result);
    }

    public static VectorDouble power(VectorDouble v1, VectorDouble v2) {
        if (v1.size != v2.size) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = Math.pow(v1.elements[i], v2.elements[i]);
        }
        return new VectorDouble(result);
    }

    // Similarly update min/max methods:
    public VectorDouble min(VectorDouble v2) {
        if (size != v2.size) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        for (int i = 0; i < size; i++) {
            elements[i] = Math.min(elements[i], v2.elements[i]);
        }
        return this;
    }

    public static VectorDouble min(VectorDouble v1, VectorDouble v2) {
        if (v1.size != v2.size) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = Math.min(v1.elements[i], v2.elements[i]);
        }
        return new VectorDouble(result);
    }

    public VectorDouble max(VectorDouble v2) {
        if (size != v2.size) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        for (int i = 0; i < size; i++) {
            elements[i] = Math.max(elements[i], v2.elements[i]);
        }
        return this;
    }

    public static VectorDouble max(VectorDouble v1, VectorDouble v2) {
        if (v1.size != v2.size) {
            throw new IllegalArgumentException("Vectors must have the same length");
        }
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = Math.max(v1.elements[i], v2.elements[i]);
        }
        return new VectorDouble(result);
    }

    // Replace scalar operation methods
    public VectorDouble min(double scalar) {
        for (int i = 0; i < size; i++) {
            elements[i] = Math.min(elements[i], scalar);
        }
        return this;
    }

    public static VectorDouble min(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = Math.min(v.elements[i], scalar);
        }
        return new VectorDouble(result);
    }

    public VectorDouble max(double scalar) {
        for (int i = 0; i < size; i++) {
            elements[i] = Math.max(elements[i], scalar);
        }
        return this;
    }

    public static VectorDouble max(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = Math.max(v.elements[i], scalar);
        }
        return new VectorDouble(result);
    }

    public VectorDouble add(double scalar) {
        for (int i = 0; i < size; i++) {
            elements[i] = elements[i] + scalar;
        }
        return this;
    }

    public static VectorDouble add(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = v.elements[i] + scalar;
        }
        return new VectorDouble(result);
    }

    public VectorDouble subtract(double scalar) {
        for (int i = 0; i < size; i++) {
            elements[i] = elements[i] - scalar;
        }
        return this;
    }

    public static VectorDouble subtract(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = v.elements[i] - scalar;
        }
        return new VectorDouble(result);
    }

    public VectorDouble multiply(double scalar) {
        for (int i = 0; i < size; i++) {
            elements[i] = elements[i] * scalar;
        }
        return this;
    }

    public static VectorDouble multiply(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = v.elements[i] * scalar;
        }
        return new VectorDouble(result);
    }

    public VectorDouble divide(double scalar) {
        for (int i = 0; i < size; i++) {
            elements[i] = scalar != 0 ? elements[i] / scalar : (scalar >= 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
        }
        return this;
    }

    public static VectorDouble divide(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = scalar != 0 ? v.elements[i] / scalar : (scalar >= 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
        }
        return new VectorDouble(result);
    }

    public VectorDouble power(double scalar) {
        for (int i = 0; i < size; i++) {
            elements[i] = Math.pow(elements[i], scalar);
        }
        return this;
    }

    public static VectorDouble power(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = Math.pow(v.elements[i], scalar);
        }
        return new VectorDouble(result);
    }

    // Scalar divided by vector (1/x elementwise)
    public VectorDouble idivide(double scalar) {
        for (int i = 0; i < size; i++) {
            elements[i] = elements[i] != 0 ? scalar / elements[i] : (scalar >= 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
        }
        return this;
    }

    // Scalar divided by vector (1/x elementwise)
    public static VectorDouble divide(double scalar, VectorDouble v) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = v.elements[i] != 0 ? scalar / v.elements[i] : (scalar >= 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY);
        }
        return new VectorDouble(result);
    }

    // Scalar minus vector (scalar - each element)
    public VectorDouble isubtract(double scalar) {
        for (int i = 0; i < size; i++) {
            elements[i] = scalar - elements[i];
        }
        return this;
    }

    // Scalar minus vector (scalar - each element)
    public static VectorDouble subtract(double scalar, VectorDouble v) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = scalar - v.elements[i];
        }
        return new VectorDouble(result);
    }

    // Elementwise square root
    public VectorDouble sqrt() {
        for (int i = 0; i < size; i++) {
            elements[i] = Math.sqrt(elements[i]);
        }
        return this;
    }

    // Elementwise square root
    public static VectorDouble sqrt(VectorDouble v) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = Math.sqrt(v.elements[i]);
        }
        return new VectorDouble(result);
    }

    // Elementwise natural logarithm
    public VectorDouble log() {
        for (int i = 0; i < size; i++) {
            elements[i] = Math.log(elements[i]);
        }
        return this;
    }

    // Elementwise natural logarithm
    public static VectorDouble log(VectorDouble v) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = Math.log(v.elements[i]);
        }
        return new VectorDouble(result);
    }

    // Elementwise comparison operations
    public static VectorDouble equals(VectorDouble v1, VectorDouble v2) {
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = v1.elements[i] == v2.elements[i] ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble notEquals(VectorDouble v1, VectorDouble v2) {
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = v1.elements[i] != v2.elements[i] ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble lessThan(VectorDouble v1, VectorDouble v2) {
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = v1.elements[i] < v2.elements[i] ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble lessThanOrEqual(VectorDouble v1, VectorDouble v2) {
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = v1.elements[i] <= v2.elements[i] ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble greaterThan(VectorDouble v1, VectorDouble v2) {
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = v1.elements[i] > v2.elements[i] ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble greaterThanOrEqual(VectorDouble v1, VectorDouble v2) {
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = v1.elements[i] >= v2.elements[i] ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble equals(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = v.elements[i] == scalar ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble notEquals(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = v.elements[i] != scalar ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble lessThan(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = v.elements[i] < scalar ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble lessThanOrEqual(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = v.elements[i] <= scalar ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble greaterThan(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = v.elements[i] > scalar ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble greaterThanOrEqual(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = v.elements[i] >= scalar ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    // Elementwise logical operations
    public static VectorDouble and(VectorDouble v1, VectorDouble v2) {
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = (v1.elements[i] != 0 && v2.elements[i] != 0) ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble or(VectorDouble v1, VectorDouble v2) {
        double[] result = new double[v1.size];
        for (int i = 0; i < v1.size; i++) {
            result[i] = (v1.elements[i] != 0 || v2.elements[i] != 0) ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble not(VectorDouble v) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = v.elements[i] == 0 ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble and(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = (v.elements[i] != 0 && scalar != 0) ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static VectorDouble or(VectorDouble v, double scalar) {
        double[] result = new double[v.size];
        for (int i = 0; i < v.size; i++) {
            result[i] = (v.elements[i] != 0 || scalar != 0) ? 1.0 : 0.0;
        }
        return new VectorDouble(result);
    }

    public static boolean all(VectorDouble v) {
        for (double e : v.elements) {
            if (e == 0) {
                return false;
            }
        }
        return true;
    }

    // Check if at least one value is non-zero
    public static boolean any(VectorDouble v) {
        for (double e : v.elements) {
            if (e != 0) {
                return true;
            }
        }
        return false;
    }

    public static VectorInt nonzero(VectorDouble v) {
        // First count non-zero elements
        int count = 0;
        for (double element : v.elements) {
            if (element != 0) {
                count++;
            }
        }

        // Create array with indices of non-zero elements
        int[] indices = new int[count];
        int index = 0;
        for (int i = 0; i < v.size; i++) {
            if (v.elements[i] != 0) {
                indices[index++] = i;
            }
        }

        return new VectorInt(indices);
    }

    // Helper method for elementwise operations between two vectors
    // ToString method for easy display
    @Override
    public String toString() {
        return Arrays.toString(elements);
    }
}
