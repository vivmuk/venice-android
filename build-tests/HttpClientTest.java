import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
public class HttpClientTest {
  public static void main(String[] a) throws Exception {
    System.out.println("proxyHost=" + System.getProperty("https.proxyHost") + " user=" + System.getProperty("https.proxyUser"));
    long t0 = System.currentTimeMillis();
    try (CloseableHttpClient c = HttpClients.createSystem()) {
      HttpGet get = new HttpGet("https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/1.9.20/kotlin-stdlib-1.9.20.pom");
      try (CloseableHttpResponse r = c.execute(get)) {
        String body = EntityUtils.toString(r.getEntity());
        System.out.println("HTTPCLIENT_TEST: status=" + r.getStatusLine() + " in " + (System.currentTimeMillis()-t0) + "ms, bodyLen=" + body.length());
      }
    } catch (Exception e) {
      System.out.println("HTTPCLIENT_TEST: FAILED in " + (System.currentTimeMillis()-t0) + "ms: " + e);
    }
  }
}
