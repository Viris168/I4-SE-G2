package edu.itc.shop.api.dto;

import java.util.List;
import java.util.Map;

/** RFC 9457 problem details body, the shape defined in Task 3. */
public record Problem(String type, String title, int status, String detail, String instance,
                      List<Map<String, String>> errors) {

    public static final String MEDIA_TYPE = "application/problem+json";
}
