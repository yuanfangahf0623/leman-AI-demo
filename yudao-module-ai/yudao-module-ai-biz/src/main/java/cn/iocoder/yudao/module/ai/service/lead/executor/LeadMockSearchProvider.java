package cn.iocoder.yudao.module.ai.service.lead.executor;

import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.SearchEvidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic mock search provider used when no external search key is configured.
 */
public class LeadMockSearchProvider implements LeadSearchProvider {

    private final List<MockSite> sites = List.of(
            site("https://kleinwaschhaus.de", "wall_mounted_washing_machine", "Germany", "Klein Waschhaus GmbH",
                    "procurement@kleinwaschhaus.de", "+49 30 2201 8890", "small appliance retailer and distributor",
                    List.of("wall mounted washing machines", "mini washing machines", "compact home appliances"),
                    "Musterstrasse 12, 10115 Berlin, Germany", "B2B", "/b2b"),
            site("https://mini-lavage.fr", "wall_mounted_washing_machine", "France", "Mini Lavage Maison SARL",
                    "contact@mini-lavage.fr", "+33 1 42 68 77 20", "independent ecommerce retailer",
                    List.of("mini washing machines", "compact washing machines", "small bathroom appliances"),
                    "18 Rue du Commerce, 75015 Paris, France", "Revendeurs", "/revendeurs"),
            site("https://elettro-casa-small.it", "wall_mounted_washing_machine", "Italy", "Elettro Casa Small",
                    "sales@elettro-casa-small.it", "+39 02 8718 4501", "regional home appliance dealer",
                    List.of("small appliances", "compact washers", "bathroom laundry products"),
                    "Via Torino 44, 20123 Milano, Italy", "Distribuzione", "/distribuzione"),
            site("https://nordic-marine-supply.nl", "yacht_and_marine", "Netherlands", "Nordic Marine Supply BV",
                    "wholesale@nordic-marine-supply.nl", "+31 20 705 6102", "marine equipment supplier",
                    List.of("yacht equipment", "marine accessories", "boat interior appliances"),
                    "Havenstraat 22, 1016 Amsterdam, Netherlands", "Dealers", "/dealers"),
            site("https://adriatic-yacht-equipment.hr", "yacht_and_marine", "Croatia", "Adriatic Yacht Equipment",
                    "ceo@adriatic-yacht-equipment.hr", "+385 21 442 801", "yacht equipment distributor",
                    List.of("yacht accessories", "boat equipment", "marine appliances"),
                    "Obala kneza Branimira 4, 21000 Split, Croatia", "Distribution", "/distribution"),
            site("https://boathome-greece.gr", "yacht_and_marine", "Greece", "BoatHome Greece",
                    "info@boathome-greece.gr", "+30 210 418 5502", "boat equipment retailer",
                    List.of("boat galley appliances", "marine supplies", "yacht interior equipment"),
                    "Marina Zeas, 18536 Piraeus, Greece", "B2B", "/b2b"),
            site("https://campervan-gear.de", "camper_and_rv", "Germany", "Campervan Gear Deutschland",
                    "b2b@campervan-gear.de", "+49 89 4422 7810", "camper van accessories retailer",
                    List.of("camper van accessories", "RV equipment", "compact appliances for vans"),
                    "Schwanthalerstrasse 86, 80336 Munich, Germany", "B2B", "/b2b"),
            site("https://caravan-comfort.pl", "camper_and_rv", "Poland", "Caravan Comfort Polska",
                    "procurement@caravan-comfort.pl", "+48 22 390 4471", "caravan accessories distributor",
                    List.of("caravan accessories", "RV equipment", "small appliances for camping"),
                    "ul. Prosta 20, 00-850 Warsaw, Poland", "Dystrybucja", "/dystrybucja"),
            site("https://iberia-rv-store.es", "camper_and_rv", "Spain", "Iberia RV Store",
                    "sales@iberia-rv-store.es", "+34 91 455 6021", "RV equipment retailer",
                    List.of("RV equipment", "caravan accessories", "camping appliances"),
                    "Calle Alcala 211, 28028 Madrid, Spain", "Distribuidores", "/distribuidores"));

    private final Map<String, String> mockPages = buildMockPages();

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public List<String> search(String keyword, String country, int maxResults) {
        String category = inferCategoryFromKeyword(keyword);
        List<String> matches = new ArrayList<>();
        for (MockSite site : sites) {
            if ((category == null || category.equals(site.category()))
                    && (!LeadAgentUtils.hasText(country) || site.country().equalsIgnoreCase(country))) {
                matches.add(site.homeUrl());
            }
        }
        for (MockSite site : sites) {
            if (matches.size() >= maxResults) {
                break;
            }
            if (!matches.contains(site.homeUrl()) && (category == null || category.equals(site.category()))) {
                matches.add(site.homeUrl());
            }
        }
        for (MockSite site : sites) {
            if (matches.size() >= maxResults) {
                break;
            }
            if (!matches.contains(site.homeUrl())) {
                matches.add(site.homeUrl());
            }
        }
        return matches.stream().limit(maxResults).toList();
    }

    @Override
    public List<SearchEvidence> searchEvidence(String query, String country, int maxResults) {
        String text = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<SearchEvidence> results = new ArrayList<>();
        for (MockSite site : sites) {
            String domain = LeadAgentUtils.extractDomain(site.homeUrl());
            String slug = slug(site.company());
            boolean companyMatch = text.contains(site.company().toLowerCase(Locale.ROOT))
                    || List.of(slug.split("-")).stream().anyMatch(token -> token.length() >= 3 && text.contains(token));
            boolean domainMatch = text.contains(domain);
            if (companyMatch || domainMatch) {
                results.addAll(mockExternalMentions(site));
            }
        }
        if (results.isEmpty() && !sites.isEmpty()) {
            results.addAll(mockExternalMentions(sites.get(0)));
        }
        return results.stream().limit(maxResults).toList();
    }

    @Override
    public Map<String, String> getMockPages() {
        return mockPages;
    }

    private Map<String, String> buildMockPages() {
        Map<String, String> pages = new LinkedHashMap<>();
        for (MockSite site : sites) {
            String base = site.homeUrl();
            pages.put(base, pageHtml(site, "home"));
            pages.put(base + "/about", pageHtml(site, "about"));
            pages.put(base + "/contact", pageHtml(site, "contact"));
            pages.put(base + "/impressum", pageHtml(site, "imprint"));
            pages.put(base + site.extraPagePath(), pageHtml(site, "extra"));
        }
        return pages;
    }

    private List<SearchEvidence> mockExternalMentions(MockSite site) {
        String slug = slug(site.company());
        String products = String.join(", ", site.products().stream().limit(2).toList());
        List<SearchEvidence> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : mockSocialProfiles(site).entrySet()) {
            result.add(SearchEvidence.builder()
                    .url(entry.getValue())
                    .title(site.company() + " on " + entry.getKey())
                    .snippet("Official public " + entry.getKey() + " page for " + site.company()
                            + ". Recent 2026 posts mention " + products + " and business updates.")
                    .build());
        }
        result.add(SearchEvidence.builder()
                .url("https://www.eu-retail-directory.example/" + slug)
                .title(site.company() + " European retailer profile")
                .snippet(site.company() + " listed as a " + site.customerType() + " in " + site.country() + ".")
                .build());
        result.add(SearchEvidence.builder()
                .url("https://tradefair-directory.example/exhibitors/" + slug)
                .title(site.company() + " trade fair exhibitor listing")
                .snippet(site.company() + " profile references " + products + " and distributor cooperation.")
                .build());
        return result;
    }

    private Map<String, String> mockSocialProfiles(MockSite site) {
        String slug = slug(site.company());
        return Map.of(
                "facebook", "https://www.facebook.com/" + slug,
                "tiktok", "https://www.tiktok.com/@" + slug,
                "xiaohongshu", "https://www.xiaohongshu.com/user/profile/" + slug);
    }

    private String pageHtml(MockSite site, String pageType) {
        String products = String.join(", ", site.products());
        String socialLinks = mockSocialProfiles(site).entrySet().stream()
                .map(entry -> "<a href=\"" + entry.getValue() + "\">" + entry.getKey() + "</a>")
                .reduce("", (left, right) -> left + " " + right);
        String nav = "<nav><a href=\"/\">Home</a> <a href=\"/about\">About</a> "
                + "<a href=\"/contact\">Contact</a> <a href=\"" + site.extraPagePath() + "\">"
                + site.extraPageLabel() + "</a> <a href=\"/impressum\">Impressum</a></nav>";
        String body;
        if ("contact".equals(pageType)) {
            body = "<h1>Contact " + site.company() + "</h1><p>Email: " + site.email() + "</p><p>Phone: "
                    + site.phone() + "</p><p>Address: " + site.address() + "</p>"
                    + "<p>Our team welcomes B2B cooperation requests from retailers and distributors.</p>";
        } else if ("about".equals(pageType)) {
            body = "<h1>About " + site.company() + "</h1><p>" + site.company() + " is a "
                    + site.customerType() + " in " + site.country() + ".</p><p>Main products include "
                    + products + ".</p>";
        } else if ("imprint".equals(pageType)) {
            body = "<h1>Impressum</h1><p>" + site.company() + "</p><p>" + site.address()
                    + "</p><p>Business contact: " + site.email() + "</p><p>" + site.phone() + "</p>";
        } else if ("extra".equals(pageType)) {
            body = "<h1>" + site.extraPageLabel() + "</h1><p>We support wholesale, distributor, dealer "
                    + "and B2B cooperation for " + products + ".</p><p>Please contact " + site.email()
                    + " for purchasing information.</p>";
        } else {
            body = "<h1>" + site.company() + "</h1><p>" + site.company() + " sells " + products
                    + " through an independent online shop and local store.</p><p>Based in " + site.country()
                    + ", we serve retail customers, dealers and small wholesale partners.</p>";
        }
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>" + site.company()
                + " - " + site.customerType() + "</title><meta name=\"description\" content=\""
                + site.company() + " offers " + products + " in " + site.country() + ".\"></head><body>"
                + nav + "<main>" + body + "</main><footer>" + site.company() + " | " + site.address()
                + " | " + socialLinks + "</footer></body></html>";
    }

    private String inferCategoryFromKeyword(String keyword) {
        String text = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        if (text.contains("yacht") || text.contains("marine") || text.contains("boat")) {
            return "yacht_and_marine";
        }
        if (text.contains("camper") || text.contains("rv") || text.contains("caravan")
                || text.contains("camping") || text.contains("motorhome")) {
            return "camper_and_rv";
        }
        if (text.contains("washing") || text.contains("washer")) {
            return "wall_mounted_washing_machine";
        }
        if (text.contains("appliance") || text.contains("kitchen") || text.contains("bathroom")
                || text.contains("electrical") || text.contains("smart home")) {
            return "appliance_dealers";
        }
        if (text.contains("hotel") || text.contains("guesthouse") || text.contains("serviced apartment")
                || text.contains("long stay apartment")) {
            return "hotel_and_serviced_apartment";
        }
        if (text.contains("real estate") || text.contains("interior") || text.contains("renovation")
                || text.contains("housing") || text.contains("tiny house")) {
            return "real_estate_and_interior";
        }
        return null;
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static MockSite site(String homeUrl, String category, String country, String company, String email,
                                 String phone, String customerType, List<String> products, String address,
                                 String extraPageLabel, String extraPagePath) {
        return new MockSite(homeUrl, category, country, company, email, phone, customerType, products, address,
                extraPageLabel, extraPagePath);
    }

    private record MockSite(String homeUrl, String category, String country, String company, String email,
                            String phone, String customerType, List<String> products, String address,
                            String extraPageLabel, String extraPagePath) {
    }

}
