package pl.sca.fix.integration.git;

public record GitCheckoutInfo(String remoteUrl, String branch, String commitSha) {
}
