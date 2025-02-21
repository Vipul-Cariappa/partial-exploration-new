package de.tum.in.pet.util;

public class MatrixDouble {
    double[][] elements;

    MatrixDouble(int r, int c) {
        elements = new double[r][c];
    }

    int row_count() {
        return elements.length;
    }

    int col_count() {
        return elements[0].length;
    }

    int size() {
        return row_count() * col_count();
    }

    static MatrixDouble zeros(int r, int c) {
        return new MatrixDouble(r, c);
    }

    // Method to index a specific element
    double index(int row, int col) {
        if (row < 0 || row >= row_count() || col < 0 || col >= col_count()) {
            throw new IndexOutOfBoundsException("Index out of bounds");
        }
        return elements[row][col];
    }

    // Method to slice a submatrix
    MatrixDouble slice(int rowStart, int rowEnd, int colStart, int colEnd) {
        if (rowStart < 0 || rowEnd > row_count() || rowStart > rowEnd ||
                colStart < 0 || colEnd > col_count() || colStart > colEnd) {
            throw new IllegalArgumentException("Invalid slice range");
        }
        MatrixDouble subMatrix = new MatrixDouble(rowEnd - rowStart, colEnd - colStart);
        for (int i = rowStart; i < rowEnd; i++) {
            System.arraycopy(elements[i], colStart, subMatrix.elements[i - rowStart], 0, colEnd - colStart);
        }
        return subMatrix;
    }

    // Method to get the nth row
    VectorDouble getRow(int n) {
        if (n < 0 || n >= row_count()) {
            throw new IndexOutOfBoundsException("Row index out of bounds");
        }
        return new VectorDouble(elements[n]);
    }

    // Method to get the nth column
    VectorDouble getColumn(int n) {
        if (n < 0 || n >= col_count()) {
            throw new IndexOutOfBoundsException("Column index out of bounds");
        }
        double[] column = new double[row_count()];
        for (int i = 0; i < row_count(); i++) {
            column[i] = elements[i][n];
        }
        return new VectorDouble(column);
    }

    // Method to set a specific element
    void set(int row, int col, double value) {
        if (row < 0 || row >= row_count() || col < 0 || col >= col_count()) {
            throw new IndexOutOfBoundsException("Index out of bounds");
        }
        elements[row][col] = value;
    }

    // Method to set an entire row
    void setRow(int row, VectorDouble vector) {
        if (row < 0 || row >= row_count()) {
            throw new IndexOutOfBoundsException("Row index out of bounds");
        }
        if (vector.size() != col_count()) {
            throw new IllegalArgumentException("Vector length must match matrix column count");
        }
        System.arraycopy(vector.elements, 0, elements[row], 0, col_count());
    }

    // Method to set an entire column
    void setColumn(int col, VectorDouble vector) {
        if (col < 0 || col >= col_count()) {
            throw new IndexOutOfBoundsException("Column index out of bounds");
        }
        if (vector.size() != row_count()) {
            throw new IllegalArgumentException("Vector length must match matrix row count");
        }
        for (int i = 0; i < row_count(); i++) {
            elements[i][col] = vector.elements[i];
        }
    }

    // Method to set multiple rows using start and end indices
    void setRows(int startRow, int endRow, MatrixDouble matrix) {
        if (startRow < 0 || endRow > row_count() || startRow > endRow) {
            throw new IllegalArgumentException("Invalid row range");
        }
        if (matrix.row_count() != endRow - startRow || matrix.col_count() != col_count()) {
            throw new IllegalArgumentException("Matrix dimensions mismatch");
        }
        for (int i = 0; i < endRow - startRow; i++) {
            System.arraycopy(matrix.elements[i], 0, elements[startRow + i], 0, col_count());
        }
    }

    // Method to set multiple columns using start and end indices
    void setColumns(int startCol, int endCol, MatrixDouble matrix) {
        if (startCol < 0 || endCol > col_count() || startCol > endCol) {
            throw new IllegalArgumentException("Invalid column range");
        }
        if (matrix.col_count() != endCol - startCol || matrix.row_count() != row_count()) {
            throw new IllegalArgumentException("Matrix dimensions mismatch");
        }
        for (int i = 0; i < row_count(); i++) {
            for (int j = 0; j < endCol - startCol; j++) {
                elements[i][startCol + j] = matrix.elements[i][j];
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < row_count(); i++) {
            if (i > 0) {
                sb.append(" ");
            }
            sb.append("[");
            for (int j = 0; j < col_count(); j++) {
                sb.append(String.format("%.6f", elements[i][j]));
                if (j < col_count() - 1) {
                    sb.append(", ");
                }
            }
            sb.append("]");
            if (i < row_count() - 1) {
                sb.append("\n");
            }
        }

        sb.append("]");
        return sb.toString();
    }
}
