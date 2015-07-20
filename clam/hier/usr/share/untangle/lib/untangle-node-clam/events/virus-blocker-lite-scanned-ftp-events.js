{
    "category": "Virus Blocker Lite",
    "conditions": [
        {
            "column": "virus_blocker_lite_clean",
            "javaClass": "com.untangle.node.reporting.SqlCondition",
            "operator": "is",
            "value": "NOT NULL"
        }
    ],
    "defaultColumns": ["time_stamp","hostname","username","uri","virus_blocker_lite_clean","virus_blocker_lite_name","s_server_addr","s_server_port"],
    "description": "All FTP sessions scanned by Virus Blocker Lite.",
    "displayOrder": 30,
    "javaClass": "com.untangle.node.reporting.EventEntry",
    "table": "ftp_events",
    "title": "Scanned Ftp Events",
    "uniqueId": "virus-blocker-lite-KX1K3Q0IOC"
}
