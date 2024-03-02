package com.uiptv.util;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class LogUtil {
    public static void httpLog(String url, HttpRequest request, HttpResponse<String> response, Map<String, String> params) {
        System.out.println("URL: " + url);
        System.out.println();
//        System.out.println("==========================================Status Code==========================================");
//        System.out.println(response.statusCode());
//        System.out.println("==========================================End Status Code==========================================");
//        System.out.println();
        System.out.println("==========================================Request Headers==========================================");
        System.out.println();
        System.out.print(request.headers());
        System.out.println();
        System.out.println("==========================================End Request Headers==========================================");
        System.out.println();
        System.out.println("==========================================Request Body==========================================");
        System.out.println();
        System.out.print(params);
        System.out.println();
        System.out.println("==========================================End Request Body==========================================");
        System.out.println();
        System.out.println("==========================================Response Headers==========================================");
        System.out.println();
        System.out.print(response.headers());
        System.out.println();
        System.out.println("==========================================End Response Headers==========================================");
        System.out.println();
        System.out.println("==========================================Response Body==========================================");
        System.out.println();
        System.out.print(response.body());
        System.out.println();
        System.out.println("==========================================End Response Body==========================================");
        System.out.println();
    }
}
