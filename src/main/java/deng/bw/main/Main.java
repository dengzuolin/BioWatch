package deng.bw.main;

import de.felixroske.jfxsupport.AbstractJavaFxApplicationSupport;
import javafx.stage.Stage;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class})
//@SpringBootApplication
@ComponentScan({"deng.bw.service","deng.bw.main"})
@EnableScheduling
public class Main extends AbstractJavaFxApplicationSupport {
    public static Controller CTRL;

    @Override
    public void start(Stage primaryStage) throws Exception{
        super.start(primaryStage);
    }

    public static void main(String[] args) {
        launch(Main.class, MainView.class, args);
    }
}