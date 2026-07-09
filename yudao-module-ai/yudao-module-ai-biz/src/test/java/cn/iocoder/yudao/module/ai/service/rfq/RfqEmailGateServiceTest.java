package cn.iocoder.yudao.module.ai.service.rfq;

import cn.iocoder.yudao.module.ai.framework.rfq.AiRfqProperties;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.EmailRawDTO;
import cn.iocoder.yudao.module.ai.service.rfq.dto.HermesRfqDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RfqEmailGateServiceTest {

    @Test
    void evaluateBeforeHermesShouldRejectInternalSenderDomain() {
        RfqEmailGateService service = new RfqEmailGateService(new AiRfqProperties());

        RfqEmailGateService.GateResult result = service.evaluateBeforeHermes(EmailRawDTO.builder()
                .from("Fang Yuan <yuanf@leman-tech.com>")
                .build(), EmailDTO.builder()
                .subject("RFQ CNC part")
                .bodyText("Please quote CNC part x100")
                .build());

        assertFalse(result.isPassed());
    }

    @Test
    void evaluateBeforeHermesShouldRejectInternalSenderDomainWithAtPrefixConfig() {
        AiRfqProperties properties = new AiRfqProperties();
        properties.getDetection().setInternalEmailDomains(List.of("@lecho-gmbh.de"));
        RfqEmailGateService service = new RfqEmailGateService(properties);

        RfqEmailGateService.GateResult result = service.evaluateBeforeHermes(EmailRawDTO.builder()
                .from("Sales <sales@lecho-gmbh.de>")
                .build(), EmailDTO.builder()
                .subject("RFQ CNC part")
                .bodyText("Please quote CNC part x100")
                .build());

        assertFalse(result.isPassed());
    }

    @Test
    void evaluateBeforeHermesShouldRejectWithoutExplicitInquiryIntent() {
        RfqEmailGateService service = new RfqEmailGateService(new AiRfqProperties());

        RfqEmailGateService.GateResult result = service.evaluateBeforeHermes(EmailRawDTO.builder()
                .from("Olivia <olivia.odonnell@wordly.ai>")
                .build(), EmailDTO.builder()
                .subject("Follow up from Wordly")
                .bodyText("Sharing more details about the translation service.")
                .build());

        assertFalse(result.isPassed());
    }

    @Test
    void evaluateBeforeHermesShouldPassExternalInquiryIntent() {
        RfqEmailGateService service = new RfqEmailGateService(new AiRfqProperties());

        RfqEmailGateService.GateResult result = service.evaluateBeforeHermes(EmailRawDTO.builder()
                .from("Buyer <buyer@example.com>")
                .build(), EmailDTO.builder()
                .subject("RFQ CNC part")
                .bodyText("Please quote CNC part x100.")
                .build());

        assertTrue(result.isPassed());
    }

    @Test
    void evaluateBeforeHermesShouldPassKnownRfqAndRfqReplySubjects() {
        RfqEmailGateService service = new RfqEmailGateService(new AiRfqProperties());
        List<String> subjects = List.of(
                "Re: ep64973 - Audi SSP41F Accu.head RFQ",
                "Re: FW: HMG study - Accu head",
                "RFQ - JG Ball (Leman)",
                "RFQ- CONNECTION BLOCK - HISS NOISE -RIO22 - 251024",
                "eP 56793 MMA HVAC 59845 MBEAM HVAC- design change study",
                "ep64973 - Audi SSP41F Accu.head RFQ",
                "Hanon Systems RFQ (Request for Quotation) for Pin - Hinge @Leman",
                "HMG accumulator head & can quotation",
                "R744 2-BLK HSG Offer",
                "RE: Re: Cartridge type flange, cover RFQ (Leman)",
                "RE: Re: Hanon Systems RFQ (Request for Quotation) for Pin - Hinge @Leman",
                "RE: RFQ- CONNECTION BLOCK - HISS NOISE -RIO22 - 251024",
                "RE: RFQ MMA fitting head FC2J0MMA1A03 Price Consultation (1)",
                "RE: RFQ MMA fitting head FC2J0MMA1A03 Price Consultation"
        );

        for (String subject : subjects) {
            RfqEmailGateService.GateResult result = service.evaluateBeforeHermes(EmailRawDTO.builder()
                    .from("Buyer <buyer@example.com>")
                    .build(), EmailDTO.builder()
                    .subject(subject)
                    .bodyText("Please process this inquiry.")
                    .build());

            assertTrue(result.isPassed(), subject);
        }
    }

    @Test
    void evaluateBeforeCreateShouldRejectInternalCustomer() {
        RfqEmailGateService service = new RfqEmailGateService(new AiRfqProperties());

        RfqEmailGateService.GateResult result = service.evaluateBeforeCreate(HermesRfqDTO.builder()
                .isRfq(true)
                .customer("LECHO GmbH")
                .products(List.of(HermesRfqDTO.Product.builder().name("Wordly").build()))
                .build());

        assertFalse(result.isPassed());
    }

    @Test
    void evaluateBeforeCreateShouldRejectChineseInternalCustomer() {
        RfqEmailGateService service = new RfqEmailGateService(new AiRfqProperties());

        RfqEmailGateService.GateResult result = service.evaluateBeforeCreate(HermesRfqDTO.builder()
                .isRfq(true)
                .customer("理文科技（山东）股份有限公司")
                .products(List.of(HermesRfqDTO.Product.builder().name("CNC part").build()))
                .build());

        assertFalse(result.isPassed());
    }

    @Test
    void evaluateBeforeCreateShouldRejectShortChineseInternalCustomer() {
        RfqEmailGateService service = new RfqEmailGateService(new AiRfqProperties());

        RfqEmailGateService.GateResult result = service.evaluateBeforeCreate(HermesRfqDTO.builder()
                .isRfq(true)
                .customer("理文科技")
                .products(List.of(HermesRfqDTO.Product.builder().name("CNC part").build()))
                .build());

        assertFalse(result.isPassed());
    }

}
