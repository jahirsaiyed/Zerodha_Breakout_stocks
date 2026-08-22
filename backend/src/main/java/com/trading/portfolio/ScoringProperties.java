package com.trading.portfolio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "portfolio.scoring")
public class ScoringProperties {

    private double proximityWeight = 0.6;
    private double riskRewardWeight = 0.4;

    public double getProximityWeight() { return proximityWeight; }
    public void setProximityWeight(double proximityWeight) { this.proximityWeight = proximityWeight; }

    public double getRiskRewardWeight() { return riskRewardWeight; }
    public void setRiskRewardWeight(double riskRewardWeight) { this.riskRewardWeight = riskRewardWeight; }
}
