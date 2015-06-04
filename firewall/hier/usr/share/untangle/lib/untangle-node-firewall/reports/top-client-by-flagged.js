{
    "uniqueId": "firewall-gsR2R5tGWw",
    "category": "Firewall",
    "description": "The number of flagged session grouped by client.",
    "displayOrder": 501,
    "enabled": true,
    "javaClass": "com.untangle.node.reporting.ReportEntry",
    "orderByColumn": "value",
    "orderDesc": true,
    "units": "hits",
    "pieGroupColumn": "c_client_addr",
    "pieSumColumn": "sum(firewall_flagged::int)",
    "preCompileResults": false,
    "conditions": [
        {
            "javaClass": "com.untangle.uvm.node.SqlCondition",
            "column": "firewall_rule_index",
            "operator": "is",
            "value": "not null"
        }
    ],
    "readOnly": true,
    "table": "sessions",
    "title": "Top Flagged Clients",
    "type": "PIE_GRAPH"
}
