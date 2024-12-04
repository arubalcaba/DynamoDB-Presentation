package dynamotaco.api;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import dynamotaco.models.*;
import dynamotaco.util.TacoUtil;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class GetAllOrdersHandler  implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDb = DynamoDbClient.create();
    private static final String TABLE_NAME = System.getenv("TABLE_NAME");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private Context loggingContext;


    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            loggingContext = context;
            loggingContext.getLogger().log("Retrieving orders");
            Map<String, String> queryParams = request.getQueryStringParameters();
            String email = queryParams.get("email");

            if (email == null) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("Missing email");
            }

            List<Order> orders = getAllOrders(email);

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of(
                            "Access-Control-Allow-Headers", "*",
                            "Access-Control-Allow-Origin", "*",
                            "Access-Control-Allow-Methods", "*"
                    ))
                    .withBody(MAPPER.writeValueAsString(orders));
        } catch (Exception e) {
            context.getLogger().log("Error retrieving ordesr: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("Error retrieving orders");
        }

    }

    private List<Order> getAllOrders(String email) {
        loggingContext.getLogger().log("Retrieving orders for email: " + email);
        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :skPrefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.builder().s("CUSTOMER#" + email).build(),
                        ":skPrefix", AttributeValue.builder().s("ORDER#").build()
                ))
                .build();

        QueryResponse response = dynamoDb.query(queryRequest);

        List<Order> orders = response.items().stream()
                .map(TacoUtil::mapToOrder)
                .collect(Collectors.toList());


        return orders;
    }



}
