
import java.io.IOException;
import java.sql.SQLException;
import org.guanzon.appdriver.base.GRiderCAS;
import org.guanzon.appdriver.base.GuanzonException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ph.com.guanzongroup.querymanager.cas.base.GQuery;

public class QueryManagerTest {
    static GRiderCAS instance;
    static GQuery trans;
    
    public QueryManagerTest() {
    }
    
    @BeforeAll
    public static void setUpClass() throws SQLException, GuanzonException, IOException {
        System.out.println("setUpClass()");
        String path;
        
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            path = "D:/GGC_Maven_Systems";
        } else {
            path = "/srv/GGC_Maven_Systems";
        }
        
        System.setProperty("sys.default.path.config", path);
        
        instance = new GRiderCAS();

        if (!instance.loadEnv("IntegSys")){
            System.err.println(instance.getMessage());
            System.exit(1);
        }
        
        if (!instance.logUser("IntegSys", "M001000001")){
            System.err.println(instance.getMessage());
            System.exit(1);
        }
        
        trans = new GQuery();
        trans.setGRiderX(instance);
    }
    
    @AfterAll
    public static void tearDownClass() {
    }
    
    @BeforeEach
    public void setUp() {
    }
    
    @AfterEach
    public void tearDown() {
    }
    
    @Test
    public void testQuery() throws SQLException{
        System.out.println("-----testQuery()-----");
        
        if (!trans.execute("SELECT * FROM Branch", true, "")){
            System.out.println(trans.getMessage());
            Assertions.fail();
        }
        
    }   
}
