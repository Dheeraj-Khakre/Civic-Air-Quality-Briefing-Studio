package com.civicair.briefing.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import com.civicair.briefing.ingestion.DatasetEntity;
import com.civicair.briefing.ingestion.ObservationEntity;
import com.civicair.briefing.ingestion.QualityIssueEntity;

@Component
public class DatasetQualityValidator {
    public List<QualityIssueEntity> detectOutliers(DatasetEntity dataset) {
        List<QualityIssueEntity> issues = new ArrayList<>();
        Map<String, List<ObservationEntity>> groups = dataset.getObservations().stream()
                .filter(row -> row.getNormalizedValue() != null && !row.isDuplicate())
                .collect(Collectors.groupingBy(ObservationEntity::getPollutant));
        groups.forEach((pollutant, observations) -> {
            if (observations.size() < 4) return;
            List<Double> sorted = observations.stream().map(ObservationEntity::getNormalizedValue).sorted().toList();
            double lowerQuartile = percentile(sorted, 0.25);
            double upperQuartile = percentile(sorted, 0.75);
            double spread = upperQuartile - lowerQuartile;
            double lowerBound = lowerQuartile - 1.5 * spread;
            double upperBound = upperQuartile + 1.5 * spread;
            observations.stream().filter(row -> row.getNormalizedValue() < lowerBound || row.getNormalizedValue() > upperBound)
                    .forEach(row -> issues.add(new QualityIssueEntity(dataset.getId(), row.getRowNumber(),
                            "POTENTIAL_OUTLIER", "WARNING",
                            pollutant + " measurement is outside the 1.5 IQR screening range and was retained.")));
        });
        return issues;
    }

    private double percentile(List<Double> values, double percentile) {
        double position = percentile * (values.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        return values.get(lower) + (values.get(upper) - values.get(lower)) * (position - lower);
    }
}
