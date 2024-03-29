import reports.sql_helper as sql_helper

@sql_helper.print_timing
def generate_tables():
    __create_http_events()

@sql_helper.print_timing
def cleanup_tables(cutoff):
    sql_helper.clean_table("http_events", cutoff)
    
@sql_helper.print_timing
def __create_http_events():
    sql_helper.create_table("""\
CREATE TABLE reports.http_events (
    request_id bigint NOT NULL,
    time_stamp timestamp NOT NULL,
    session_id bigint,
    client_intf int2,
    server_intf int2,
    c_client_addr inet,
    s_client_addr inet,
    c_server_addr inet,
    s_server_addr inet,
    c_client_port integer,
    s_client_port integer,
    c_server_port integer,
    s_server_port integer,
    client_country text,
    client_latitude real,
    client_longitude real,
    server_country text,
    server_latitude real,
    server_longitude real,
    policy_id int2,
    username text,
    hostname text,
    method character(1),
    uri text,
    host text,
    domain text,
    referer text,
    c2s_content_length bigint,
    s2c_content_length bigint,
    s2c_content_type text,
    s2c_content_filename text,
    ad_blocker_cookie_ident text,
    ad_blocker_action character,
    web_filter_reason character(1),
    web_filter_category_id int2,
    web_filter_rule_id int2,
    web_filter_blocked boolean,
    web_filter_flagged boolean,
    virus_blocker_lite_clean boolean,
    virus_blocker_lite_name text,
    virus_blocker_clean boolean,
    virus_blocker_name text,
    threat_prevention_blocked boolean,
    threat_prevention_flagged boolean,
    threat_prevention_rule_id integer,
    threat_prevention_reputation int2,
    threat_prevention_categories integer)""",
                                ["request_id"],
                                ["session_id",
                                 "policy_id",
                                 "time_stamp",
                                 "host",
                                 "domain",
                                 "username",
                                 "hostname",
                                 "c_client_addr",
                                 "client_intf",
                                 "server_intf",
                                 "web_filter_blocked",
                                 "web_filter_flagged",
                                 "web_filter_category_id",
                                 "virus_blocker_clean",
                                 "virus_blocker_lite_clean",
                                 "threat_prevention_flagged",
                                 "threat_prevention_blocked",
                                 "ad_blocker_action"])
    sql_helper.add_column('http_events','threat_prevention_blocked','boolean') # 15.0
    sql_helper.add_column('http_events','threat_prevention_flagged','boolean') # 15.0
    sql_helper.add_column('http_events','threat_prevention_rule_id','integer') # 15.0
    sql_helper.add_column('http_events','threat_prevention_reputation','int2') # 15.0
    sql_helper.add_column('http_events','threat_prevention_categories','integer') # 15.0
    sql_helper.add_column('http_events','client_country','text') # 15.0
    sql_helper.add_column('http_events','client_latitude','real') # 15.0
    sql_helper.add_column('http_events','client_longitude','real') # 15.0
    sql_helper.add_column('http_events','server_country','text') # 15.0
    sql_helper.add_column('http_events','server_latitude','real') # 15.0
    sql_helper.add_column('http_events','server_longitude','real') # 15.0


