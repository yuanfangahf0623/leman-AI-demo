package cn.iocoder.yudao.module.ai.service.lead.executor;

import cn.iocoder.yudao.module.ai.service.lead.executor.LeadAgentExecutionModels.LeadRecord;

import java.util.Map;

/**
 * Drafts a human-review development email.
 */
public class LeadEmailGenerator {

    private static final Map<String, String> PRODUCT_DIRECTION_BY_CATEGORY = Map.of(
            "wall_mounted_washing_machine", "compact and wall-mounted washing machine products",
            "yacht_and_marine", "compact laundry and home appliance products for yacht and marine spaces",
            "camper_and_rv", "compact appliance products for RV, caravan and camper van use",
            "appliance_dealers", "compact home appliance and laundry products",
            "hotel_and_serviced_apartment", "compact laundry appliances for guest rooms, apartments and small spaces",
            "real_estate_and_interior", "compact laundry appliance solutions for furnished apartments and space-limited homes");
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "wall_mounted_washing_machine", "compact home appliances",
            "yacht_and_marine", "yacht sales and marine equipment",
            "camper_and_rv", "RV, motorhome and camper equipment",
            "appliance_dealers", "home appliance distribution and retail",
            "hotel_and_serviced_apartment", "hotel and serviced apartment procurement",
            "real_estate_and_interior", "real estate, interior and compact housing projects");

    public EmailDraft generateDevelopmentEmail(LeadRecord lead) {
        String company = LeadAgentUtils.hasText(lead.getCompanyName()) ? lead.getCompanyName() : "Team";
        String category = CATEGORY_LABELS.getOrDefault(lead.getMatchedCategory(), "your product category");
        String productDirection = PRODUCT_DIRECTION_BY_CATEGORY.getOrDefault(lead.getMatchedCategory(),
                "compact home appliance products");
        String mainProducts = lead.getMainProducts().isEmpty()
                ? "your current product range" : String.join(", ", lead.getMainProducts().stream().limit(3).toList());
        String subject = "Cooperation opportunity for compact home appliance products";
        String body = "Hi " + company + ",\n\n"
                + "I found your company while looking for European retailers and distributors in " + category + ".\n\n"
                + "We are a manufacturer/supplier focusing on " + productDirection + ". I noticed that your business "
                + "is related to " + mainProducts + ", so I wanted to check whether there may be a potential "
                + "cooperation opportunity.\n\n"
                + "If you are interested, I can send a brief product introduction and pricing information for your review.\n\n"
                + "Best regards,\nLeman Tech Sales Team";
        return new EmailDraft(subject, body);
    }

    public record EmailDraft(String subject, String body) {
    }

}
