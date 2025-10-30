package io.materia.validation.reporting

import io.materia.validation.models.ProductionReadinessReport
import kotlin.math.roundToInt

/**
 * Generates JavaScript chart scripts for HTML reports.
 */
internal class ChartGenerator {

    fun generateScript(report: ProductionReadinessReport): String = """
        <script>
            const ctx = document.getElementById('scoresChart');
            if (ctx) {
                new Chart(ctx, {
                    type: 'bar',
                    data: {
                        labels: [${report.categories.joinToString { "'${it.name}'" }}],
                        datasets: [{
                            label: 'Score (%)',
                            data: [${
        report.categories.joinToString {
            (it.score * 100).roundToInt().toString()
        }
    }],
                            backgroundColor: [
                                'rgba(102, 126, 234, 0.8)',
                                'rgba(118, 75, 162, 0.8)',
                                'rgba(40, 167, 69, 0.8)',
                                'rgba(255, 193, 7, 0.8)',
                                'rgba(220, 53, 69, 0.8)',
                                'rgba(23, 162, 184, 0.8)'
                            ],
                            borderColor: [
                                'rgba(102, 126, 234, 1)',
                                'rgba(118, 75, 162, 1)',
                                'rgba(40, 167, 69, 1)',
                                'rgba(255, 193, 7, 1)',
                                'rgba(220, 53, 69, 1)',
                                'rgba(23, 162, 184, 1)'
                            ],
                            borderWidth: 1
                        }]
                    },
                    options: {
                        scales: {
                            y: {
                                beginAtZero: true,
                                max: 100
                            }
                        },
                        plugins: {
                            legend: {
                                display: false
                            }
                        }
                    }
                });
            }
        </script>
    """
}
