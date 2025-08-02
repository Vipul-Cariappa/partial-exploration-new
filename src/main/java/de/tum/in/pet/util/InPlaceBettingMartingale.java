package de.tum.in.pet.util;

public class InPlaceBettingMartingale {
    VectorDouble samples = new VectorDouble();
    long samples_count;
    double alpha;
    double prior_mean;
    double prior_variance;
    double fake_obs;
    double scale;

    VectorDouble samples_cumulative_sum;
    VectorDouble samples_mean_diff_sq;
    VectorDouble mu_hat_t = new VectorDouble();
    VectorDouble sigma2_t;
    VectorDouble lambda = new VectorDouble();

    public InPlaceBettingMartingale(double alpha, double prior_mean, double prior_variance, double fake_obs, double scale) {
        this.alpha = alpha;
        this.prior_mean = prior_mean;
        this.prior_variance = prior_variance;
        this.fake_obs = fake_obs;
        this.scale = scale;
        this.samples_count = 0;
        this.samples_cumulative_sum = new VectorDouble(0);
        this.samples_mean_diff_sq = new VectorDouble(0);
        this.sigma2_t = new VectorDouble(prior_variance);
    }

    public void AddObservation(double sample) {
        samples = VectorDouble.append(samples, sample);
        samples_count += 1;

        double sample_cumulative_sum = sample + samples_cumulative_sum.at(samples_cumulative_sum.size() - 1);
        samples_cumulative_sum = VectorDouble.append(samples_cumulative_sum, sample_cumulative_sum);

        // lazy compute mut_hat_t
        double mu_hat_t_i = Math.min((sample_cumulative_sum + fake_obs + prior_mean) / (samples_count + fake_obs), 1);
        mu_hat_t = VectorDouble.append(mu_hat_t, mu_hat_t_i);
        
        // lazy compute sigma2_t
        double sample_mean_diff_sq = Math.pow(sample - mu_hat_t_i, 2) + samples_mean_diff_sq.at(samples_mean_diff_sq.size() - 1);
        samples_mean_diff_sq = VectorDouble.append(samples_mean_diff_sq, sample_mean_diff_sq);
        double sigma2_t_i = ((sample_mean_diff_sq) + (fake_obs * prior_variance)) / (samples_count + fake_obs);
        sigma2_t = VectorDouble.append(sigma2_t, sigma2_t_i);

        // lazy compute lambda
        double lambda_i = Math.sqrt((2 * Math.log(1 / alpha)) / ((samples.size() * Math.log(samples.size() + 1)) * (sigma2_t_i)));
        lambda = VectorDouble.append(lambda, lambda_i);
    }

    public Pair<Double, Double> confidence_width(int breaks, double break_start, double break_stop, boolean running_intersection, double theta, double trunc_scale) {
        Pair<VectorDouble, VectorDouble> r = BettingMartingale.confidence_sequence_from_martingale(samples, lambda, breaks, break_start, break_stop, alpha, running_intersection, theta, trunc_scale);
        VectorDouble l = r.first;
        VectorDouble u = r.second;
        // return VectorDouble.index(u, u.size() - 1) - VectorDouble.index(l, l.size() - 1);
        return new Pair<>(VectorDouble.index(l, l.size() - 1), VectorDouble.index(u, u.size() - 1));
    }

    private boolean is_in_interval(double x) {
        VectorDouble mart = BettingMartingale.diversified_betting_mart(samples, lambda, x, alpha, 0.5, 0.5);
        double value = mart.at(mart.size() - 1);
        return value < (1 / alpha);
    }

    private double derivative(double x, double delta) {
        VectorDouble mart_neg = BettingMartingale.diversified_betting_mart(samples, lambda, x - delta, alpha, 0.5, 0.5);
        VectorDouble mart_pos = BettingMartingale.diversified_betting_mart(samples, lambda, x + delta, alpha, 0.5, 0.5);
        double d_neg = mart_neg.at(mart_neg.size() - 1);
        double d_pos = mart_pos.at(mart_pos.size() - 1);
        return d_pos - d_neg;
    }

    public double heuristic_search(double low, double high, int iter_count, boolean find_low, double precision, double delta) {
        while ((precision <= (high - low)) && (iter_count >= 0)) {
            iter_count--;
            double mid = (high + low) / 2;
            if (is_in_interval(mid)) {
                if (find_low)
                    high = mid;
                else
                    low = mid;
            } else {
                double compute_delta = Math.min(delta, (high - low) / 8);
                double df = derivative(mid, compute_delta);
                if (df > 0)
                    high = mid;
                else
                    low = mid;
            }
        }
        if (find_low)
            return low;
        return high;
    }

    public Pair<Double, Double> confidence_width(double low, double high) {
        int iter_count = 20;
        double precision = 1e-8;
        double delta = 1e-6;
        return new Pair<>(heuristic_search(low, high, iter_count, true, precision, delta), heuristic_search(low, high, iter_count, false, precision, delta));
    }
}
