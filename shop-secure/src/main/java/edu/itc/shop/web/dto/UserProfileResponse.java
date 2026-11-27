package edu.itc.shop.web.dto;

import java.util.List;

public record UserProfileResponse(String name, List<String> roles) {}
