package schnerry.seymouranalyzer;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import schnerry.seymouranalyzer.data.ColorDatabase;
import schnerry.seymouranalyzer.data.CollectionManager;

public class Seymouranalyzer implements ModInitializer {
	public static final String MOD_ID = "seymouranalyzer";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Seymour Analyzer...");

		// Load config
		schnerry.seymouranalyzer.config.ClothConfig.getInstance().load();

		// Initialize color database
		ColorDatabase.getInstance();

		// Initialize collection
		CollectionManager.getInstance();


		LOGGER.info("Seymour Analyzer initialized successfully!");
	}
}