package de.tum.in.pet.util;

import java.util.ArrayList;
import prism.Pair;

public class InPlaceBettingMartingale {
    public VectorDouble samples = new VectorDouble();
    long last_computed_at = 0;
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
    double base_aggregation_count = 5;

    // private int decide_aggregation_count2() {
    //     double current_mean = (confidence.second + confidence.first) / 2.0;
    //     double mean = (current_mean <= 0.5) ? current_mean : (1 - current_mean);
    //     double aggregation_count = mean * 198 + 1; // using range [1, 100]
    //     double adjusted_confidence_width = Math.pow(confidence.second - confidence.first, 8.0);
    //     double aggregate_count_adjusted_to_confidence = (1 - adjusted_confidence_width) * aggregation_count + adjusted_confidence_width * base_aggregation_count;
    //     // System.out.println("mean: " + current_mean + " confidence width: " + (confidence.second - confidence.first) + " aggregation count: " + aggregate_count_adjusted_to_confidence);
    //     return (int)Math.round(aggregate_count_adjusted_to_confidence);
    // }
    
    private int decide_aggregation_count() {
        double confidence_width = confidence.second - confidence.first;
        double aggregate_count = (1 - confidence_width) * 98 + 1;
        return (int)Math.round(aggregate_count);
    }

    public InPlaceBettingMartingale(double alpha, int aggregate) {
        this.alpha = alpha;
        this.aggregate = aggregate;

        prior_mean = 0.5;
        prior_variance = 0.25;
        fake_obs = 1;
        scale = 1;
        samples_count = 0;
        samples_cumulative_sum = 0;
        samples_mean_diff_sq = 0;
        confidence = new Pair<>(0.0, 1.0);
        aggregate_cache = new ArrayList<>();
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

    public void observe(double sample) {
        double mean = sample;
        if (aggregate != 1) {
            aggregate_cache.add(sample);
            double aggregate = this.aggregate;
            if (aggregate < 1)
                aggregate = decide_aggregation_count();
            // else if (aggregate == -2)
            //     aggregate = decide_aggregation_count2();
            if (aggregate_cache.size() < aggregate)
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
        samples_mean_diff_sq += Math.pow(sample - mu_hat_t, 2.0);
        double sigma2_t = (samples_mean_diff_sq + (fake_obs * prior_variance)) / (samples_count + fake_obs);

        // lazy compute lambda
        double lambda_i = Math.sqrt((2.0 * Math.log(2.0 / alpha)) / ((samples.size() * Math.log(samples.size() + 1.0)) * (sigma2_t)));
        lambda.append(lambda_i);
    }

    public void recompute(double alpha) {
        this.alpha = alpha;
        VectorDouble samples = this.samples;

        // clear existing data
        samples_count = 0;
        samples_cumulative_sum = 0;
        samples_mean_diff_sq = 0;
        last_computed_at = 0;
        confidence = new Pair<>(0.0, 1.0);
        aggregate_cache = new ArrayList<>();
        lambda = new VectorDouble(samples.size());
        this.samples = new VectorDouble(samples.size());

        // add observation and compute confidence_with at the same time
        for (int i = 0; i < samples.size(); i++) {
            observe(samples.at(i));
            getConfidenceWidth();
        }
    }

    private Pair<Double, Double> confidence_width(int breaks, double break_start, double break_stop, boolean running_intersection, double theta, double trunc_scale) {
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

    private Pair<Double, Double> confidence_width(double low, double high) {
        int iter_count = 10; // precision: 2 ^ (-iter_count)
        double precision = 1e-4;
        double delta = 1e-6;
        return new Pair<>(heuristic_search(low, high, iter_count, true, precision, delta), heuristic_search(low, high, iter_count, false, precision, delta));
    }

    public Pair<Double, Double> getConfidenceWidth() {
        if (samples.size() > last_computed_at) {
            last_computed_at = samples.size();
            confidence = confidence_width(confidence.first, confidence.second);
            // if ((confidence.first < 0.0) || (confidence.first > 1.0))
            //     System.out.println("Illegal value of lower bound of confidence width");
        }
        return confidence;
    }
}
