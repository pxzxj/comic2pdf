package io.github.pxzxj;

import blazing.chain.LZSEncoding;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Comic2Pdf {

    private static final String baseFolder = "D:/";

    private static final RestTemplate httpTemplate = new RestTemplate();
    private static RestTemplate httpsTemplate;

    private static final ExecutorService executorService = Executors.newFixedThreadPool(20);

    public static void main(String[] args) {
        initRestTemplate();
        crawlImage("神武纪1", "https://www.maofly.com/manga/27877/294871.html");
        crawlImage("神武纪2", "https://www.maofly.com/manga/2453/10810.html");
        executorService.shutdown();
    }

    static void initRestTemplate() {
        SSLContext sslContext = null;
        try {
            sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            }).build();
        } catch (Exception e) {
            e.printStackTrace();
        }
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, new String[] { "TLSv1" }, null,
                new NoopHostnameVerifier());
        CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
        HttpComponentsClientHttpRequestFactory httpfactory = new HttpComponentsClientHttpRequestFactory(httpclient);
        httpsTemplate = new RestTemplate(httpfactory);
    }

    static void crawlImage(String name, String initUrl) {
        Pattern chapterPattern = Pattern.compile("data-chapter_num=\"(\\d+)\" data-chapter-type=\"(\\d)\"");
        Pattern urlPattern = Pattern.compile("\"url\":\"(.*)\"");
        Pattern imagePattern = Pattern.compile("img_data = \"(.+?)\"");
        Pattern imageNamePattern = Pattern.compile("/([^/]+\\.jpg)");
        String url = initUrl;
        List<String> images = new ArrayList<>();
        while (true) {
            String html = httpTemplate.getForObject(url, String.class);
            Matcher matcher = imagePattern.matcher(html);
            if (matcher.find()) {
                images.add(matcher.group(1));
            }
            matcher = chapterPattern.matcher(html);
            if (matcher.find()) {
                String chapterId = matcher.group(1);
                String chapterType = matcher.group(2);
                String chapterUrl = "https://www.maofly.com/chapter_num?chapter_id=" + chapterId + "&ctype=1&type=" + chapterType;
                String chapterResponse = httpTemplate.getForObject(chapterUrl, String.class);
                matcher = urlPattern.matcher(chapterResponse);
                if (matcher.find()) {
                    url = matcher.group(1).replace("\\", "");
                    if (!StringUtils.hasText(url)) {
                        break;
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        System.out.println("images.size() = " + images.size());
        for (int i = 0; i < images.size(); i++) {
            File folder = new File(baseFolder + name + "/第" + (i + 1) + "卷/");
            folder.mkdirs();
            String[] arr = LZSEncoding.decompressFromBase64(images.get(i)).split(",");
            for (String s : arr) {
                Matcher matcher = imageNamePattern.matcher(s);
                if(matcher.find()) {
                    String imageName = matcher.group(1);
                    String imageUri = "https://mao.mhtupian.com/uploads/" + s;
                    executorService.submit(new GetImage(i, name, imageName, imageUri));
                } else {
                    throw new RuntimeException("invalid image name");
                }
            }
        }
    }

    static class GetImage implements Runnable {


        int index;
        String key;
        String imageName;
        String imageUri;

        public GetImage(int index, String key, String imageName, String imageUri) {
            this.index = index;
            this.key = key;
            this.imageName = imageName;
            this.imageUri = imageUri;
        }

        @Override
        public void run() {
            System.out.println("imageUri = " + imageUri);
            HttpHeaders headers = new HttpHeaders();
            headers.add("authority", "mao.mhtupian.com");
            headers.add("referer", "https://www.maofly.com/");
            ResponseEntity<byte[]> exchange = httpsTemplate.exchange(imageUri, HttpMethod.GET, new HttpEntity<>(null, headers), byte[].class);
            byte[] bytes = exchange.getBody();
            File file = new File(baseFolder + key + "/第" + (index + 1) + "卷/" + imageName);
            try {
                FileCopyUtils.copy(bytes, file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
