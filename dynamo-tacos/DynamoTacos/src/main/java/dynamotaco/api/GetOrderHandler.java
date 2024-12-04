package dynamotaco.api;
import dynamotaco.models.*;
import dynamotaco.util.TacoUtil;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;


public class GetOrderHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            Map<String, String> queryParams = request.getQueryStringParameters();
            String email = queryParams.get("email");
            String orderId = queryParams.get("orderId");

            if (email == null || orderId == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("Missing email or orderId");
            }

            String partitionKey = "CUSTOMER#" + email;
            String sortKey = "ORDER#" + orderId;

            Map<String, AttributeValue> key = new HashMap<>();
            key.put("PK", AttributeValue.builder().s(partitionKey).build());
            key.put("SK", AttributeValue.builder().s(sortKey).build());

            GetItemRequest getItemRequest = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(key)
                .build();

            GetItemResponse response = dynamoDb.getItem(getItemRequest);

            if (!response.hasItem()) {
                return new APIGatewayProxyResponseEvent()
                    .withStatusCode(404)
                    .withBody("Order not found");
            }

            Map<String, AttributeValue> item = response.item();
            Order order = TacoUtil.mapToOrder(item);

            return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(Map.of(
                    "Access-Control-Allow-Headers", "*",
                    "Access-Control-Allow-Origin", "*",
                    "Access-Control-Allow-Methods", "*"
                ))
                .withBody(MAPPER.writeValueAsString(order));

        } catch (Exception e) {
            context.getLogger().log("Error retrieving order: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                .withStatusCode(500)
                .withBody("Error retrieving order");
        }
    }
}