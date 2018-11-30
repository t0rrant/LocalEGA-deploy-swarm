package se.nbis.lega.deployment.lega;

import org.gradle.api.tasks.TaskAction;
import se.nbis.lega.deployment.Groups;
import se.nbis.lega.deployment.LocalEGATask;

import java.io.IOException;

public class CreateMQConfigurationTask extends LocalEGATask {

    public CreateMQConfigurationTask() {
        super();
        this.setGroup(Groups.LEGA_PUBLIC.name());
    }

    @TaskAction
    public void run() throws IOException {
        createConfig(Config.DEFS_JSON.getName(), getProject().file("mq/defs.json"));
        createConfig(Config.RABBITMQ_CONFIG.getName(), getProject().file("mq/rabbitmq.config"));
        createConfig(Config.ENTRYPOINT_SH.getName(), getProject().file("mq/entrypoint.sh"));
        String cegaMQPassword = readTrace(getProject().file("../cega/.tmp/.trace"), "CEGA_MQ_PASSWORD");
        if (cegaMQPassword != null) {
            writeTrace("CEGA_CONNECTION", String.format("amqp://lega:%s@cega-mq:5672/lega", cegaMQPassword));
        }
    }

}
