package bo.edu.univalle.sis.ue6dejunio_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling is on for the notification stream's heartbeat: an open connection whose browser is
 * gone only reveals itself when something is written to it, so something has to write.
 */
@SpringBootApplication
@EnableScheduling
public class Ue6dejunioApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(Ue6dejunioApiApplication.class, args);
	}

}
