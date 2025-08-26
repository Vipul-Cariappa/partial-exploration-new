package de.tum.in.pet.util;

public class InPlaceBettingMartingale {
    VectorDouble samples = new VectorDouble();
    long samples_count;
    double alpha;
    double prior_mean;
    double prior_variance;
    double fake_obs;
    double scale;

    double samples_cumulative_sum;
    double samples_mean_diff_sq;
    VectorDouble lambda = new VectorDouble();

    public InPlaceBettingMartingale(double alpha, double prior_mean, double prior_variance, double fake_obs, double scale) {
        this.alpha = alpha;
        this.prior_mean = prior_mean;
        this.prior_variance = prior_variance;
        this.fake_obs = fake_obs;
        this.scale = scale;
        this.samples_count = 0;
        this.samples_cumulative_sum = 0;
        this.samples_mean_diff_sq = 0;
    }

    public void AddObservation(double sample) {
        samples.append(sample);
        samples_count += 1;

        samples_cumulative_sum += sample;

        // lazy compute mut_hat_t
        double mu_hat_t = Math.min((samples_cumulative_sum + fake_obs + prior_mean) / (samples_count + fake_obs), 1);
        
        // lazy compute sigma2_t
        samples_mean_diff_sq += Math.pow(sample - mu_hat_t, 2);
        double sigma2_t = (samples_mean_diff_sq + (fake_obs * prior_variance)) / (samples_count + fake_obs);

        // lazy compute lambda
        double lambda_i = Math.sqrt((2 * Math.log(1 / alpha)) / ((samples.size() * Math.log(samples.size() + 1)) * (sigma2_t)));
        lambda.append(lambda_i);
    }

    public Pair<Double, Double> confidence_width(int breaks, double break_start, double break_stop, boolean running_intersection, double theta, double trunc_scale) {
        Pair<VectorDouble, VectorDouble> r = BettingMartingale.confidence_sequence_from_martingale(samples, lambda, breaks, break_start, break_stop, alpha, running_intersection, theta, trunc_scale);
        VectorDouble l = r.first;
        VectorDouble u = r.second;
        // return VectorDouble.index(u, u.size() - 1) - VectorDouble.index(l, l.size() - 1);
        return new Pair<>(VectorDouble.index(l, l.size() - 1), VectorDouble.index(u, u.size() - 1));
    }

    public double heuristic_search(double low, double high, int iter_count, boolean find_low, double precision, double delta) {
        while ((precision <= (high - low)) && (iter_count >= 0)) {
            iter_count--;
            double mid = (high + low) / 2;
            double mart_mid = BettingMartingale.diversified_betting_mart(samples, lambda, mid, alpha, 0.5, 0.5).back();
            if (mart_mid < (1 / alpha)) {
                if (find_low)
                    high = mid;
                else
                    low = mid;
            } else {
                double compute_delta = Math.min(delta, (high - low) / 8);
                boolean pos = true;
                if (mid + compute_delta > 1) {
                    compute_delta = -compute_delta;
                    pos = false;
                }
                double mart_mid_plus_h = BettingMartingale.diversified_betting_mart(samples, lambda, mid + compute_delta, alpha, 0.5, 0.5).back();
                double df = 0;
                if (pos) {
                    df = mart_mid_plus_h - mart_mid;
                } else {
                    df = mart_mid - mart_mid_plus_h;
                }
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
        int iter_count = 10; // precision: 2 ^ (-iter_count)
        double precision = 1e-8;
        double delta = 1e-6;
        return new Pair<>(heuristic_search(low, high, iter_count, true, precision, delta), heuristic_search(low, high, iter_count, false, precision, delta));
    }
}
