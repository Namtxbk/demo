package com.example.demo.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class ORATRNRoute extends RouteBuilder {
    @Value("${endpoint.url}")
    private String endpoint;

    @Override
    public void configure() throws Exception {
        //from("timer://fetchOratrnData?repeatCount=1")
        from("quartz://myGroup/ORATRN?cron=0+0+0+*+*+?")

                .setBody(constant("""
            SELECT
                at.acctg_trans_id,
                at.transaction_date,
                at.posted_date,
                at.is_posted,
                at.acctg_trans_type_enum_id,
                at.amount_uom_id,
                at.organization_party_id,
                ate.party_id,
                ate.product_store_id,
                ate.acctg_trans_entry_seq_id,
                ate.gl_account_id,
                ate.debit_credit_flag,
                ate.amount
            FROM acctg_trans at
            JOIN acctg_trans_entry ate ON at.acctg_trans_id = ate.acctg_trans_id
            LEFT JOIN gl_account ga ON ate.gl_account_id = ga.gl_account_id
            WHERE at.transaction_date::date = CURRENT_DATE
        """))
                .to("jdbc:dataSource")
                .process(this::mapToOratrnFormat)
                .marshal().json()
                .log("Sent payload to ORATRN Data: ${body}")
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .toD(endpoint + "/oratrn");
    }

    private void mapToOratrnFormat(Exchange exchange) {
        List<Map<String, Object>> ofbizData = exchange.getIn().getBody(List.class);
        List<Map<String, Object>> oratrnData = new ArrayList<>();

        SimpleDateFormat dateFormatYYMMDD = new SimpleDateFormat("yyMMdd");
        SimpleDateFormat timeFormat = new SimpleDateFormat("HHmmss");
        Date currentDate = new Date();

        for (Map<String, Object> row : ofbizData) {
            Map<String, Object> oratrnEntry = new LinkedHashMap<>();

            Date transDate = (Date) row.get("transaction_date");
           oratrnEntry.put("ITFFIL", "II100190724.0000001");

            oratrnEntry.put("ITFLAG", mapIsPost( (String) row.get("is_posted")));
            oratrnEntry.put("ITHCOD", row.get("amount_uom_id"));
            oratrnEntry.put("ITRLTP", "W");
            oratrnEntry.put("ITRLOC", row.get("product_store_id"));
            oratrnEntry.put("ITRCEN", 1);
            oratrnEntry.put("ITRDAT", Integer.parseInt(dateFormatYYMMDD.format(transDate)));
            oratrnEntry.put("ITRTYP", 42);
            oratrnEntry.put("INUMBR", 0);// chua ro
            oratrnEntry.put("ITRQTY", 1);
            oratrnEntry.put("ITRRET", row.get("amount"));
            oratrnEntry.put("ITRCST", 1);
            oratrnEntry.put("IDEPT", 160);
            oratrnEntry.put("ITRREF", "145462");// chua ro
            oratrnEntry.put("LGUSER", row.get("party_id"));
            oratrnEntry.put("ITCOMP", 100);// chua ro
            oratrnEntry.put("ITRSDT", Integer.parseInt(dateFormatYYMMDD.format(transDate)));// chua ro
            oratrnEntry.put("ITRSTM", Integer.parseInt(timeFormat.format(currentDate)));// chua ro

            oratrnData.add(oratrnEntry);
        }

        exchange.getIn().setBody(oratrnData);
    }


    private  String mapIsPost(String isPost)
    {
        if (isPost == null) return "N";

        if (isPost== "Y") {return "P";}
        else if (isPost == "N") {return "N";}
        return "N";
    }
}
