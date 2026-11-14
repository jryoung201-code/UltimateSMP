package shop.blinksmp.network;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
/** SQL adaptation is limited to BlinkNetwork's fixed queries; no player text is interpolated. */
final class DatabaseDialect {
    final boolean mysql;
    private DatabaseDialect(boolean mysql){this.mysql=mysql;}
    static DatabaseDialect from(String url){
        if(url!=null&&url.startsWith("jdbc:postgresql:"))return new DatabaseDialect(false);
        if(url!=null&&(url.startsWith("jdbc:mysql:")||url.startsWith("jdbc:mariadb:"))){
            if(Pattern.compile("(?i)[?&]useAffectedRows=(?:false|0)(?:&|$)").matcher(url).find())
                throw new IllegalArgumentException("MySQL requires useAffectedRows=true for revision fencing");
            return new DatabaseDialect(true);
        }
        throw new IllegalArgumentException("Use a PostgreSQL, MySQL or MariaDB JDBC URL");
    }
    String prefix(){return mysql?"blinksmp_network_v1_":"blinksmp_network_v1.";}
    String driver(){return mysql?"org.mariadb.jdbc.Driver":"org.postgresql.Driver";}
    String url(String url){return mysql?url.replaceFirst("^jdbc:mysql:","jdbc:mariadb:"):url;}
    String sql(String value){
        if(!mysql)return value;
        String sql=value.replace("blinksmp_network_v1.",prefix());
        sql=sql.replace("now()","CURRENT_TIMESTAMP(6)");
        Matcher interval=Pattern.compile("interval '([0-9]+) (seconds?|minutes?)'").matcher(sql);
        sql=interval.replaceAll(m->"INTERVAL "+m.group(1)+" "+(m.group(2).startsWith("second")?"SECOND":"MINUTE"));
        if(sql.contains(" ON CONFLICT DO NOTHING")){
            Matcher insert=Pattern.compile("INSERT INTO [A-Za-z0-9_]+\\(([^,)]+)").matcher(sql);
            if(!insert.find())throw new IllegalArgumentException("Unknown insert shape");
            String column=insert.group(1);
            sql=sql.replace(" ON CONFLICT DO NOTHING"," ON DUPLICATE KEY UPDATE "+column+"="+column);
        }
        sql=sql.replaceAll("ON CONFLICT\\([^)]*\\) DO UPDATE SET","ON DUPLICATE KEY UPDATE");
        return sql.replaceAll("(?i)excluded\\.([a-z_]+)","VALUES($1)");
    }
    void uuid(PreparedStatement statement,int index,UUID value)throws SQLException{
        if(mysql)statement.setString(index,value==null?null:value.toString());else statement.setObject(index,value);
    }
    static UUID uuid(ResultSet result,int index)throws SQLException{
        String value=result.getString(index);return value==null?null:UUID.fromString(value);
    }
}
