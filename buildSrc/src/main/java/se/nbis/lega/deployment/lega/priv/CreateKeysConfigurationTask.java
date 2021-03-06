package se.nbis.lega.deployment.lega.priv;

import org.apache.commons.io.FileUtils;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.sig.Features;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.openpgp.*;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.PGPDigestCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair;
import org.gradle.api.tasks.TaskAction;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CreateKeysConfigurationTask extends LegaPrivateTask {

    @TaskAction
    public void run() throws Exception {
        getProject().file(".tmp/pgp/").mkdirs();
        createConfig(Config.KEYS_CERT.getName(), getProject().getParent().file("common/.tmp/ssl/keys.cert"));
        createConfig(Config.KEYS_KEY.getName(), getProject().getParent().file("common/.tmp/ssl/keys.key"));
        String pgpPassphrase = UUID.randomUUID().toString().replace("-", "");
        writeTrace(PGP_PASSPHRASE, pgpPassphrase);
        generatePGPKeyPair("ega", pgpPassphrase);
        createConfig(Config.EGA_SEC.getName(), getProject().file(".tmp/pgp/ega.sec"));
        generatePGPKeyPair("ega2", pgpPassphrase);
        createConfig(Config.EGA2_SEC.getName(), getProject().file(".tmp/pgp/ega2.sec"));
        String masterPassphrase = UUID.randomUUID().toString().replace("-", "");
        writeTrace(LEGA_PASSWORD, masterPassphrase);

        File egaSharedSec = getProject().file(".tmp/pgp/ega.shared.pass");
        FileUtils.write(egaSharedSec, masterPassphrase, Charset.defaultCharset());
        createConfig(Config.EGA_SHARED_PASS.getName(), egaSharedSec);

        File egaSecPass = getProject().file(".tmp/pgp/ega.sec.pass");
        FileUtils.write(egaSecPass, pgpPassphrase, Charset.defaultCharset());
        createConfig(Config.EGA_SEC_PASS.getName(), egaSharedSec);

        File ega2SecPass = getProject().file(".tmp/pgp/ega2.sec.pass");
        FileUtils.write(ega2SecPass, pgpPassphrase, Charset.defaultCharset());
        createConfig(Config.EGA2_SEC_PASS.getName(), egaSharedSec);
    }

    private void generatePGPKeyPair(String userId, String passphrase) throws Exception {
        PGPKeyRingGenerator generator = createPGPKeyRingGenerator(userId, passphrase.toCharArray());

        PGPPublicKeyRing pkr = generator.generatePublicKeyRing();
        ByteArrayOutputStream pubOut = new ByteArrayOutputStream();
        pkr.encode(pubOut);
        pubOut.close();

        PGPSecretKeyRing skr = generator.generateSecretKeyRing();
        ByteArrayOutputStream secOut = new ByteArrayOutputStream();
        skr.encode(secOut);
        secOut.close();

        byte[] armoredPublicBytes = armorByteArray(pubOut.toByteArray());
        byte[] armoredSecretBytes = armorByteArray(secOut.toByteArray());

        File pubFile = getProject().file(String.format(".tmp/pgp/%s.pub", userId));
        FileUtils.write(pubFile, new String(armoredPublicBytes), Charset.defaultCharset());

        File secFile = getProject().file(String.format(".tmp/pgp/%s.sec", userId));
        FileUtils.write(secFile, new String(armoredSecretBytes), Charset.defaultCharset());
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        Files.setPosixFilePermissions(secFile.toPath(), perms);
    }

    private PGPKeyRingGenerator createPGPKeyRingGenerator(String userId, char[] passphrase)
        throws Exception {
        RSAKeyPairGenerator keyPairGenerator = new RSAKeyPairGenerator();

        keyPairGenerator.init(
            new RSAKeyGenerationParameters(BigInteger.valueOf(0x10001), new SecureRandom(), 2048,
                12));

        PGPKeyPair rsaKeyPair =
            new BcPGPKeyPair(PGPPublicKey.RSA_GENERAL, keyPairGenerator.generateKeyPair(),
                new Date());

        PGPSignatureSubpacketGenerator signHashGenerator = new PGPSignatureSubpacketGenerator();
        signHashGenerator.setKeyFlags(false, KeyFlags.SIGN_DATA | KeyFlags.CERTIFY_OTHER);
        signHashGenerator.setFeature(false, Features.FEATURE_MODIFICATION_DETECTION);

        PGPSignatureSubpacketGenerator encryptHashGenerator = new PGPSignatureSubpacketGenerator();
        encryptHashGenerator.setKeyFlags(false, KeyFlags.ENCRYPT_COMMS | KeyFlags.ENCRYPT_STORAGE);

        PGPDigestCalculator sha1DigestCalculator =
            new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1);
        PGPDigestCalculator sha512DigestCalculator =
            new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA512);

        PBESecretKeyEncryptor secretKeyEncryptor =
            (new BcPBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256, sha512DigestCalculator))
                .build(passphrase);

        return new PGPKeyRingGenerator(PGPSignature.NO_CERTIFICATION, rsaKeyPair, userId,
            sha1DigestCalculator, encryptHashGenerator.generate(), null,
            new BcPGPContentSignerBuilder(rsaKeyPair.getPublicKey().getAlgorithm(),
                HashAlgorithmTags.SHA512), secretKeyEncryptor);
    }

    private byte[] armorByteArray(byte[] data) throws IOException {
        ByteArrayOutputStream encOut = new ByteArrayOutputStream();
        ArmoredOutputStream armorOut = new ArmoredOutputStream(encOut);
        armorOut.write(data);
        armorOut.flush();
        armorOut.close();
        return encOut.toByteArray();
    }

}
