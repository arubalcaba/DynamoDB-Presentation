package dynamotaco.util;

import dynamotaco.models.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class TacoUtil {

    public static Order mapToOrder(Map<String, AttributeValue> item) {
        Order order = new Order();
        String pk = item.get("PK").s();
        String sk = item.get("SK").s();

        order.setCustomerId(pk.replace("CUSTOMER#", ""));
        order.setId(sk.replace("ORDER#", ""));
        var zoneDate = ZonedDateTime.parse(item.get("OrderDate").s());
        order.setOrderDate(Date.from(zoneDate.toInstant()));
        order.setTotalPrice(Double.parseDouble(item.get("TotalPrice").n()));
        order.setStatus(OrderStatus.valueOf(item.get("Status").s()));

        // Deserialize Tacos
        if (item.containsKey("Tacos")) {
            List<Taco> tacos = item.get("Tacos").l().stream().map(tacoAttr -> {
                Map<String, AttributeValue> tacoMap = tacoAttr.m();
                Taco taco = new Taco();
                taco.setId(tacoMap.get("TacoId").s());
                taco.setName(tacoMap.get("Name").s());
                taco.setPrice(Double.parseDouble(tacoMap.get("Price").n()));

                // Deserialize Toppings
                if (tacoMap.containsKey("Toppings")) {
                    List<Topping> toppings = tacoMap.get("Toppings").l().stream().map(toppingAttr -> {
                        Map<String, AttributeValue> toppingMap = toppingAttr.m();
                        Topping topping = new Topping();
                        topping.setId(toppingMap.get("ToppingId").s());
                        topping.setName(toppingMap.get("Name").s());
                        topping.setPrice(Double.parseDouble(toppingMap.get("Price").n()));
                        return topping;
                    }).collect(Collectors.toList());
                    taco.setToppings(toppings);
                }
                return taco;
            }).collect(Collectors.toList());
            order.setTacos(tacos);
        }

        // Deserialize SideItems
        if (item.containsKey("SideItems")) {
            List<SideItem> sideItems = item.get("SideItems").l().stream().map(sideItemAttr -> {
                Map<String, AttributeValue> sideItemMap = sideItemAttr.m();
                SideItem sideItem = new SideItem();
                sideItem.setId(sideItemMap.get("SideItemId").s());
                sideItem.setName(sideItemMap.get("Name").s());
                sideItem.setPrice(Double.parseDouble(sideItemMap.get("Price").n()));
                return sideItem;
            }).collect(Collectors.toList());
            order.setSideItems(sideItems);
        }

        return order;
    }
}
