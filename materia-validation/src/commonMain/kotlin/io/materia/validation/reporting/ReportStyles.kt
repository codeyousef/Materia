package io.materia.validation.reporting

/**
 * Provides CSS styles and chart scripts for HTML reports.
 */
internal class ReportStyles {

    fun getHtmlStyles(): String = """
        <style>
            * {
                margin: 0;
                padding: 0;
                box-sizing: border-box;
            }

            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;
                line-height: 1.6;
                color: #333;
                background: #f5f5f5;
            }

            .header {
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                color: white;
                padding: 2rem;
                text-align: center;
            }

            .header h1 {
                margin-bottom: 0.5rem;
            }

            .timestamp, .project-path {
                opacity: 0.9;
                font-size: 0.9rem;
            }

            .section {
                max-width: 1200px;
                margin: 2rem auto;
                padding: 2rem;
                background: white;
                border-radius: 8px;
                box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            }

            .section h2 {
                color: #667eea;
                margin-bottom: 1rem;
                padding-bottom: 0.5rem;
                border-bottom: 2px solid #f0f0f0;
            }

            .summary-card {
                padding: 2rem;
                border-radius: 8px;
                text-align: center;
                position: relative;
            }

            .summary-card.ready {
                background: #d4edda;
                border: 1px solid #c3e6cb;
                color: #155724;
            }

            .summary-card.not-ready {
                background: #f8d7da;
                border: 1px solid #f5c6cb;
                color: #721c24;
            }

            .status-text {
                font-size: 2rem;
                font-weight: bold;
                margin-bottom: 1rem;
            }

            .overall-score {
                font-size: 1.2rem;
            }

            table {
                width: 100%;
                border-collapse: collapse;
            }

            th, td {
                padding: 0.75rem;
                text-align: left;
                border-bottom: 1px solid #ddd;
            }

            th {
                background: #f8f9fa;
                font-weight: 600;
            }

            tr.passed, tr.excellent {
                background: #d4edda;
            }

            tr.failed, tr.poor {
                background: #f8d7da;
            }

            tr.good {
                background: #d1ecf1;
            }

            tr.warning {
                background: #fff3cd;
            }

            .platform-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                gap: 1rem;
                margin-top: 1rem;
            }

            .platform-card {
                padding: 1rem;
                border-radius: 4px;
                border: 1px solid #ddd;
            }

            .platform-card.passed {
                background: #d4edda;
                border-color: #c3e6cb;
            }

            .platform-card.failed {
                background: #f8d7da;
                border-color: #f5c6cb;
            }

            .platform-card h3 {
                margin-bottom: 0.5rem;
            }

            .severity-group {
                margin-bottom: 1.5rem;
            }

            .severity-group.critical h3 {
                color: #dc3545;
            }

            .severity-group.high h3 {
                color: #fd7e14;
            }

            .severity-group.medium h3 {
                color: #ffc107;
            }

            .severity-group.low h3 {
                color: #6c757d;
            }

            .issue {
                padding: 1rem;
                margin-bottom: 0.5rem;
                background: #f8f9fa;
                border-left: 3px solid #667eea;
                border-radius: 4px;
            }

            .issue-header {
                display: flex;
                gap: 1rem;
                margin-bottom: 0.5rem;
            }

            .issue-category, .issue-type {
                display: inline-block;
                padding: 0.25rem 0.5rem;
                background: #667eea;
                color: white;
                border-radius: 4px;
                font-size: 0.85rem;
            }

            .issue-description {
                margin-bottom: 0.5rem;
            }

            .issue-location {
                font-family: monospace;
                color: #666;
                font-size: 0.9rem;
            }

            .issue-remediation {
                margin-top: 0.5rem;
                padding-top: 0.5rem;
                border-top: 1px dashed #ddd;
                color: #28a745;
            }

            .metadata-list dt {
                float: left;
                width: 200px;
                font-weight: 600;
                clear: left;
            }

            .metadata-list dd {
                margin-left: 220px;
                margin-bottom: 0.5rem;
            }

            .footer {
                text-align: center;
                padding: 2rem;
                color: #666;
                font-size: 0.9rem;
            }
        </style>
    """

    fun getChartScripts(): String = """
        <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    """
}
