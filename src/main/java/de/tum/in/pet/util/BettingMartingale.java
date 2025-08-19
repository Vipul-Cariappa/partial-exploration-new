package de.tum.in.pet.util;

public class BettingMartingale {
    VectorDouble samples;
    double cacheAlpha = -1.0;
    double cacheGridWidth = -1.0;
    int cacheSize = 0;
    Pair<Double, Double> confidenceWidth = new Pair<Double,Double>(-1.0, -1.0);

    BettingMartingale() {
        samples = new VectorDouble();
    }

    public void observe(double x) {
        samples.append(x);  // TODO: add aggregation
    }

    public Pair<Double, Double> confidenceWidth(double alpha, double gridWidth) {
        if (cacheSize == samples.size() && cacheAlpha == alpha && cacheGridWidth == gridWidth)
            return confidenceWidth;
        cacheSize = samples.size();
        cacheAlpha = alpha;
        cacheGridWidth = gridWidth;
        confidenceWidth = BettingMartingale.confidence_width(
            samples,
            (int)(1.0/gridWidth),
            0.0,
            1.0,
            alpha,
            true,
            0.5,
            0.5
        );
        return confidenceWidth;
    }

    static VectorDouble betting_mart(VectorDouble x, VectorDouble lambdas, double m, double alpha, double theta, double trunc_scale) {
        // alpha = 0.05;
        // theta = 0.5;
        // trunc_scale = 0.5;

        VectorDouble lambda_positive = VectorDouble.min(lambdas, trunc_scale / m);
        lambda_positive.max((-trunc_scale) / (1 - m));

        VectorDouble lambda_negative = VectorDouble.min(lambdas, trunc_scale / (1 - m));
        lambda_negative.max((-trunc_scale) / m);

        VectorDouble x_minus_mu_t = VectorDouble.subtract(x, m);
        VectorDouble multiplicand_positive = lambda_positive.multiply(x_minus_mu_t).add(1);
        VectorDouble multiplicand_negative = lambda_negative.multiply(x_minus_mu_t).isubtract(1);

        VectorDouble capital_process_positive = multiplicand_positive.cumulativeProduct().replace(Double.NaN, 0);
        VectorDouble capital_process_negative = multiplicand_negative.cumulativeProduct().replace(Double.NaN, 0);

        VectorDouble capital_process;
        if (theta == 1) {
            capital_process = capital_process_positive.multiply(theta);
        } else if (theta == 0) {
            capital_process = capital_process_negative.multiply(1 - theta);
        } else {
            capital_process = capital_process_positive.multiply(theta).max(
                    capital_process_negative.multiply(1 - theta)
            );
        }

        return capital_process;
    }

    static VectorDouble diversified_betting_mart(VectorDouble x, VectorDouble lambda, double m, double alpha, double theta, double trunc_scale) {
        VectorDouble mart_positive = betting_mart(x, lambda, m, alpha, 1, trunc_scale);
        VectorDouble mart_negative = betting_mart(x, lambda, m, alpha, 0, trunc_scale);

        VectorDouble mart;
        if (theta == 1) {
            mart = mart_positive;
        } else if (theta == 0) {
            mart = mart_negative;
        } else {
            mart = mart_positive.multiply(theta).max(
                    mart_negative.multiply(1 - theta)
            );
        }

        return mart;
    }

    static Pair<VectorDouble, VectorDouble> confidence_sequence_from_martingale(VectorDouble x, VectorDouble lambda, int breaks, double break_start, double break_stop, double alpha, boolean running_intersection, double theta, double trunc_scale) {
        VectorDouble possible_m = VectorDouble.linspace(break_start, break_stop, breaks);
        MatrixDouble confseq_mtx = MatrixDouble.zeros(possible_m.size(), x.size());

        for (int i = 0; i < possible_m.size(); i++) {
            double m = VectorDouble.index(possible_m, i);
            confseq_mtx.setRow(i, VectorDouble.lessThanOrEqual(diversified_betting_mart(x, lambda, m, alpha, theta, trunc_scale), 1 / alpha));
        }

        VectorDouble l = VectorDouble.zeros(x.size());
        VectorDouble u = VectorDouble.ones(x.size());

        for (int i = 0; i < x.size(); i++) {
            VectorInt where_in_cs = VectorDouble.nonzero(confseq_mtx.getColumn(i));
            if (where_in_cs.size() == 0) {
                VectorDouble.set(l, i, 0);
                VectorDouble.set(l, i, 1);
            } else {
                VectorDouble.set(l, i, VectorDouble.index(possible_m, VectorInt.index(where_in_cs, 0)));
                VectorDouble.set(u, i, VectorDouble.index(possible_m, VectorInt.index(where_in_cs, where_in_cs.size() - 1)));
            }
        }

        l = VectorDouble.max(
                VectorDouble.subtract(l,
                        (double) 1 / breaks),
                0);
        u = VectorDouble.min(
                VectorDouble.add(u,
                        (double) 1 / breaks),
                1);


        if (running_intersection) {
            l = VectorDouble.maximumAccumulate(l);
            u = VectorDouble.minimumAccumulate(u);
        }

        return new Pair<>(l, u);
    }

    public static Pair<Double, Double> confidence_width(VectorDouble x, int breaks, double break_start, double break_stop, double alpha, boolean running_intersection, double theta, double trunc_scale) {
        VectorDouble lambda = lambda_predmix_eb(x, alpha, 0.5, 0.25, 1, 1);
        Pair<VectorDouble, VectorDouble> r = confidence_sequence_from_martingale(x, lambda, breaks, break_start, break_stop, alpha, running_intersection, theta, trunc_scale);
        VectorDouble l = r.first;
        VectorDouble u = r.second;
        return new Pair<>(VectorDouble.index(l, l.size() - 1), VectorDouble.index(u, u.size() - 1));
    }

    static VectorDouble lambda_predmix_eb(VectorDouble x, double alpha, double prior_mean, double prior_variance, double fake_obs, double scale) {
        // alpha = 0.05
        // prior_mean = 1/2
        // prior_variance = 1/4
        // fake_obs = 1
        // scale = 1
        VectorDouble t = VectorDouble.arange(1, x.size() + 1, 1);
        VectorDouble mu_hat_t = VectorDouble.min(
                VectorDouble.divide(
                        VectorDouble.add(VectorDouble.cumulativeSum(x), fake_obs + prior_mean),
                        VectorDouble.add(t, fake_obs)
                ),
                1
        );
        VectorDouble sigma2_t = VectorDouble.divide(
                VectorDouble.add(
                        VectorDouble.cumulativeSum(VectorDouble.power(VectorDouble.subtract(x, mu_hat_t), 2)),
                        fake_obs * prior_variance
                ),
                VectorDouble.add(t, fake_obs)
        );
        VectorDouble sigma2_tminus1 = VectorDouble.prepend(VectorDouble.slice(sigma2_t, 0, sigma2_t.size() - 1), prior_variance);
        VectorDouble lambdas = VectorDouble.sqrt(
                VectorDouble.divide(
                        2 * Math.log(1 / alpha),
                        VectorDouble.multiply(
                                VectorDouble.multiply(
                                        t,
                                        VectorDouble.log(VectorDouble.add(t, 1))),
                                sigma2_tminus1)
                )
        );

        lambdas = VectorDouble.replace(lambdas, Double.NaN, 0);
        return lambdas;
    }
}
