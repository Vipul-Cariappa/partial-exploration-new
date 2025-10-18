package de.tum.in.pet.util;

import java.util.ArrayList;
import prism.Pair;

public class InPlaceBettingMartingale {
    public VectorDouble samples = new VectorDouble();
    int samples_count;
    double alpha;
    double prior_mean;
    double prior_variance;
    double fake_obs;
    double scale;
    int aggregate;
    ArrayList<Double> aggregate_cache;
    Pair<Double, Double> confidence;

    double samples_cumulative_sum;
    double samples_mean_diff_sq;
    VectorDouble lambda = new VectorDouble();
    double base_aggregation_count = 15;

    private int decide_aggregation_count() {
        double current_mean = (confidence.second + confidence.first) / 2.0;
        double mean = current_mean <= 0.5 ? current_mean : 1 - current_mean;
        double aggregation_count = mean * 48 + 1; // using range [1, 25]
        double confidence_width = confidence.second - confidence.first;
        double aggregate_count_adjusted_to_confidence = (1 - confidence_width) * aggregation_count + confidence_width * base_aggregation_count;
        return (int)Math.round(aggregate_count_adjusted_to_confidence);
    }

    public InPlaceBettingMartingale(double alpha, double prior_mean, double prior_variance, double fake_obs, double scale, int aggregate) {
        this.alpha = alpha;
        this.prior_mean = prior_mean;
        this.prior_variance = prior_variance;
        this.fake_obs = fake_obs;
        this.scale = scale;
        this.samples_count = 0;
        this.samples_cumulative_sum = 0;
        this.samples_mean_diff_sq = 0;
        this.aggregate = aggregate;
        this.confidence = new Pair<>(0.0, 1.0);
        this.aggregate_cache = new ArrayList<>();
    }
    
    public InPlaceBettingMartingale(double alpha, double prior_mean, double prior_variance, double fake_obs, double scale) {
        this.alpha = alpha;
        this.prior_mean = prior_mean;
        this.prior_variance = prior_variance;
        this.fake_obs = fake_obs;
        this.scale = scale;
        this.samples_count = 0;
        this.samples_cumulative_sum = 0;
        this.samples_mean_diff_sq = 0;
        this.confidence = new Pair<>(0.0, 1.0);
        this.aggregate_cache = new ArrayList<>();
        this.aggregate = -1;
    }

    public int size() { return samples_count; }

    public void AddObservation(double sample) {
        double mean = sample;
        if (aggregate != 1) {
            aggregate_cache.add(sample);
            double aggregate = this.aggregate;
            if (aggregate == -1)
                aggregate = decide_aggregation_count();
            if (aggregate_cache.size() != aggregate)
                return;

            mean = 0;
            for (double i: aggregate_cache) {
                mean += i;
            }
            aggregate_cache.clear();
            mean = mean / aggregate;
        }

        sample = mean;

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

    public Pair<Double, Double> confidence_width() { 
        if (aggregate_cache.isEmpty() && samples.size() > 0) {
            confidence = confidence_width(confidence.first, confidence.second);
        }
        return confidence;
    }
}
