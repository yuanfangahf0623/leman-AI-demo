package cn.iocoder.yudao.module.ai.service.meeting;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.ai.framework.meeting.AiTeamsMeetingProperties;
import cn.iocoder.yudao.module.ai.service.meeting.dto.TeamsMeetingDTO;
import cn.iocoder.yudao.module.ai.service.meeting.dto.TeamsTranscriptDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class MicrosoftGraphTeamsMeetingClient implements TeamsMeetingGraphClient {

    private static final int HTTP_SUCCESS_MIN = 200;
    private static final int HTTP_SUCCESS_MAX = 299;
    private static final String GRAPH_SCOPE = "https://graph.microsoft.com/.default";

    private final AiTeamsMeetingProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MicrosoftGraphTeamsMeetingClient(AiTeamsMeetingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(resolvePositive(properties.getConnectTimeoutSeconds(), 10)))
                .build();
    }

    @Override
    public List<TeamsMeetingDTO> listMeetings(String organizerUserId, LocalDate startDate, LocalDate endDate,
                                             int limit) {
        String userId = required(organizerUserId, "organizerUserId");
        String token = accessToken();
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String select = "id,subject,start,end,organizer,onlineMeeting,onlineMeetingUrl,webLink";
        String url = graphBaseUrl() + "/users/" + encodePath(userId) + "/calendarView"
                + "?startDateTime=" + encodeQuery(startDate.atStartOfDay().toString())
                + "&endDateTime=" + encodeQuery(endDate.plusDays(1).atStartOfDay().minusNanos(1).toString())
                + "&$select=" + encodeQuery(select)
                + "&$top=" + safeLimit;
        List<TeamsMeetingDTO> meetings = new ArrayList<>();
        String nextUrl = url;
        while (StringUtils.hasText(nextUrl) && meetings.size() < safeLimit) {
            JsonNode root = getJson(nextUrl, token);
            JsonNode values = root.path("value");
            if (!values.isArray()) {
                throw new ServiceException(500, "Microsoft Graph meeting response invalid");
            }
            for (JsonNode item : values) {
                if (meetings.size() >= safeLimit) {
                    break;
                }
                meetings.add(toMeeting(item));
            }
            nextUrl = root.path("@odata.nextLink").asText(null);
        }
        return meetings;
    }

    @Override
    public Optional<TeamsTranscriptDTO> getTranscript(String organizerUserId, TeamsMeetingDTO meeting) {
        if (meeting == null || !StringUtils.hasText(meeting.getJoinUrl())) {
            return Optional.empty();
        }
        String userId = required(organizerUserId, "organizerUserId");
        String token = accessToken();
        String onlineMeetingId = firstText(meeting.getOnlineMeetingId(),
                findOnlineMeetingIdByJoinUrl(userId, meeting.getJoinUrl(), token).orElse(null));
        if (!StringUtils.hasText(onlineMeetingId)) {
            return Optional.empty();
        }
        meeting.setOnlineMeetingId(onlineMeetingId);
        String transcriptListUrl = graphBaseUrl() + "/users/" + encodePath(userId)
                + "/onlineMeetings/" + encodePath(onlineMeetingId) + "/transcripts?$top=10";
        JsonNode root = getJson(transcriptListUrl, token);
        JsonNode values = root.path("value");
        if (!values.isArray() || values.isEmpty()) {
            return Optional.empty();
        }
        JsonNode transcript = latestTranscript(values);
        String transcriptId = transcript.path("id").asText(null);
        if (!StringUtils.hasText(transcriptId)) {
            return Optional.empty();
        }
        String contentUrl = firstText(transcript.path("transcriptContentUrl").asText(null),
                graphBaseUrl() + "/users/" + encodePath(userId) + "/onlineMeetings/" + encodePath(onlineMeetingId)
                        + "/transcripts/" + encodePath(transcriptId) + "/content?$format=text/vtt");
        String content = getText(contentUrl, token, "text/vtt");
        return Optional.of(TeamsTranscriptDTO.builder()
                .transcriptId(transcriptId)
                .content(content)
                .contentType("text/vtt")
                .build());
    }

    private Optional<String> findOnlineMeetingIdByJoinUrl(String userId, String joinUrl, String token) {
        String escapedJoinUrl = joinUrl.replace("'", "''");
        String filter = "JoinWebUrl eq '" + escapedJoinUrl + "'";
        String url = graphBaseUrl() + "/users/" + encodePath(userId) + "/onlineMeetings?$filter="
                + encodeQuery(filter);
        JsonNode root = getJson(url, token);
        JsonNode values = root.path("value");
        if (!values.isArray() || values.isEmpty()) {
            return Optional.empty();
        }
        String id = values.get(0).path("id").asText(null);
        return StringUtils.hasText(id) ? Optional.of(id) : Optional.empty();
    }

    private String accessToken() {
        String tenantId = required(properties.getTenantId(), "tenantId");
        String clientId = required(properties.getClientId(), "clientId");
        String clientSecret = required(properties.getClientSecret(), "clientSecret");
        String tokenUrl = trimTrailingSlash(required(properties.getLoginBaseUrl(), "loginBaseUrl"))
                + "/" + encodePath(tenantId) + "/oauth2/v2.0/token";
        String body = "client_id=" + encodeQuery(clientId)
                + "&client_secret=" + encodeQuery(clientSecret)
                + "&scope=" + encodeQuery(GRAPH_SCOPE)
                + "&grant_type=client_credentials";
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(resolvePositive(properties.getReadTimeoutSeconds(), 120)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        String response = send(request, "Microsoft Graph token");
        try {
            String token = objectMapper.readTree(response).path("access_token").asText(null);
            if (!StringUtils.hasText(token)) {
                throw new ServiceException(500, "Microsoft Graph token response missing access_token");
            }
            return token;
        } catch (IOException ex) {
            throw new ServiceException(500, "Microsoft Graph token response invalid");
        }
    }

    private JsonNode getJson(String url, String token) {
        String response = getText(url, token, "application/json");
        try {
            return objectMapper.readTree(response);
        } catch (IOException ex) {
            throw new ServiceException(500, "Microsoft Graph JSON response invalid");
        }
    }

    private String getText(String url, String token, String accept) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(resolvePositive(properties.getReadTimeoutSeconds(), 120)))
                .header("Accept", accept)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return send(request, sanitizeUrl(url));
    }

    private String send(HttpRequest request, String operation) {
        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < HTTP_SUCCESS_MIN || response.statusCode() > HTTP_SUCCESS_MAX) {
                log.warn("Microsoft Graph call failed, operation={}, status={}", operation, response.statusCode());
                throw new ServiceException(500, "Microsoft Graph call failed");
            }
            return response.body();
        } catch (HttpTimeoutException ex) {
            log.warn("Microsoft Graph call timeout, operation={}", operation);
            throw new ServiceException(500, "Microsoft Graph call timeout");
        } catch (IOException ex) {
            log.warn("Microsoft Graph call IO failed, operation={}, errorType={}", operation,
                    ex.getClass().getSimpleName());
            throw new ServiceException(500, "Microsoft Graph call failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ServiceException(500, "Microsoft Graph call interrupted");
        }
    }

    private TeamsMeetingDTO toMeeting(JsonNode item) {
        JsonNode organizer = item.path("organizer").path("emailAddress");
        JsonNode start = item.path("start");
        JsonNode end = item.path("end");
        JsonNode onlineMeeting = item.path("onlineMeeting");
        return TeamsMeetingDTO.builder()
                .sourceMeetingId(item.path("id").asText(null))
                .onlineMeetingId(onlineMeeting.path("id").asText(null))
                .subject(defaultText(item.path("subject").asText(null), "Teams meeting"))
                .organizerName(organizer.path("name").asText(null))
                .organizerEmail(organizer.path("address").asText(null))
                .startTime(parseGraphDateTime(start.path("dateTime").asText(null)))
                .endTime(parseGraphDateTime(end.path("dateTime").asText(null)))
                .joinUrl(firstText(onlineMeeting.path("joinUrl").asText(null), item.path("onlineMeetingUrl").asText(null)))
                .webLink(item.path("webLink").asText(null))
                .build();
    }

    private JsonNode latestTranscript(JsonNode values) {
        List<JsonNode> transcripts = new ArrayList<>();
        values.forEach(transcripts::add);
        return transcripts.stream()
                .max(Comparator.comparing(item -> item.path("createdDateTime").asText("")))
                .orElse(values.get(0));
    }

    private LocalDateTime parseGraphDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            if (value.endsWith("Z") || value.contains("+")) {
                return OffsetDateTime.parse(value).toLocalDateTime();
            }
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String graphBaseUrl() {
        return trimTrailingSlash(required(properties.getGraphBaseUrl(), "graphBaseUrl"));
    }

    private static String required(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(400, "Microsoft Graph config missing: " + fieldName);
        }
        return value.trim();
    }

    private static int resolvePositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private static String encodePath(String value) {
        return encodeQuery(value).replace("+", "%20");
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private static String sanitizeUrl(String url) {
        int queryIndex = url.indexOf('?');
        return queryIndex < 0 ? url : url.substring(0, queryIndex);
    }

}
