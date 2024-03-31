# partial-explorer

Probabilistic model checker

## Build

Clone the repo

```bash
git clone https://github.com/pazhamalai/partial-exploration.git
```

Clone all the submodules

```bash
git submodule update --init --checkout --recursive
```

Build necessary submodules

```bash
cd lib/models/lib/prism/prism  # navigate to folder
make                           # build
```

Build partial-explorer

```bash
cd ../../../../..           # navigate to folder
./gradlew compileJava       # build
```

To run, reachability checker

```bash
./gradlew -p ./ run --args='reachability -m data/models/zeroconf.prism -p data/models/zeroconf.props --property correct_max --const N=1000,K=8,reset=false'
```

To run, mean-payoff checker

```bash
./gradlew -p ./ run --args='meanPayoff -m data/models/zeroconf_rewards.prism --precision 0.01 --maxReward 1 --revisitThreshold 2 -c N=40,K=10,reset=false --rewardModule avoid'
```

### Using IntelliJ

1. Select Add Configuration(Present at top right in intellij) option. Select configuration type to be Application and specify the following parameters
1. Set module as Java 11 or above
1. Classpath: partial-exploration.main
1. Main class: de.tum.in.pet.Main
1. To enable assertions, make sure Add VM options flag is enabled in Modify options. Then add "-ea" string in VM options.
1. Command line arguments: `meanPayoff -m data/models/zeroconf_rewards.prism --precision 0.01 --maxReward 1 --revisitThreshold 2 -c N=40,K=10,reset=false --rewardModule avoid`
