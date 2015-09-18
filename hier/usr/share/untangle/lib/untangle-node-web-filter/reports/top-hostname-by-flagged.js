{
    "uniqueId": "web-filter-2joT1JbMKZw",
    "category": "Web Filter",
    "conditions": [
        {
            "column": "web_filter_flagged",
            "javaClass": "com.untangle.node.reports.SqlCondition",
            "operator": "=",
            "value": "true"
        }
    ],
    "description": "The number of flagged web request grouped by hostname.",
    "displayOrder": 402,
    "enabled": true,
    "javaClass": "com.untangle.node.reports.ReportEntry",
    "orderByColumn": "value",
    "orderDesc": true,
    "units": "hits",
    "pieGroupColumn": "hostname",
    "pieSumColumn": "count(*)",
    "readOnly": true,
    "table": "http_events",
    "title": "Top Flagged Hostnames",
    "type": "PIE_GRAPH"
}
