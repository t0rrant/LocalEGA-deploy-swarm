package se.nbis.lega.deployment.cega;

import net.schmizz.sshj.common.Buffer;
import org.apache.commons.codec.digest.Crypt;
import org.apache.commons.io.FileUtils;
import org.gradle.api.tasks.TaskAction;
import se.nbis.lega.deployment.Groups;
import se.nbis.lega.deployment.LocalEGATask;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import static se.nbis.lega.deployment.Utils.writePrivateKey;
import static se.nbis.lega.deployment.Utils.writePublicKey;

public class CreateUsersConfigurationTask extends LocalEGATask {

    public CreateUsersConfigurationTask() {
        super();
        this.setGroup(Groups.CEGA.name());
    }

    @TaskAction
    public void run() throws IOException, NoSuchAlgorithmException {
        createConfig(Config.SERVER_PY.getName(), getProject().file("../../docker/images/cega/server.py"));
        createConfig(Config.USERS_HTML.getName(), getProject().file("../../docker/images/cega/users.html"));
        String johnPassword = generateUser("john");
        createConfig(Config.JOHN_YML.getName(), getProject().file(".tmp/users/john.yml"));
        writeTrace("EGA_USER_PASSWORD_JOHN", johnPassword);
        String janePassword = generateUser("jane");
        createConfig(Config.JANE_YML.getName(), getProject().file(".tmp/users/jane.yml"));
        writeTrace("EGA_USER_PASSWORD_JANE", janePassword);
    }

    private String generateUser(String username) throws NoSuchAlgorithmException, IOException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(4096, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.genKeyPair();

        byte[] keyBytes = new Buffer.PlainBuffer().putPublicKey(keyPair.getPublic()).getCompactData();
        String sshKeyString = "ssh-rsa " + Base64.getEncoder().encodeToString(keyBytes);

        String password = UUID.randomUUID().toString().replace("-", "");
        String salt = UUID.randomUUID().toString().replace("-", "");
        String hash = Crypt.crypt(password, String.format("$1$%s$", salt));

        File userYML = getProject().file(String.format(".tmp/users/%s.yml", username));
        FileUtils.writeLines(userYML, Arrays.asList("---", "password_hash: " + hash, "pubkey: " + sshKeyString));
        writePublicKey(keyPair, getProject().file(String.format(".tmp/users/%s.pub", username)));
        writePrivateKey(keyPair, getProject().file(String.format(".tmp/users/%s.sec", username)));

        return password;
    }

}