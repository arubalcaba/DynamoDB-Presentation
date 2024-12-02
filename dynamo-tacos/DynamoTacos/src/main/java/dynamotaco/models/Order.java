package dynamotaco.models;

import java.util.Date;
import java.util.List;

public class Order {
    private String id;
    private String customerId;
    private Date orderDate;
    private double totalPrice;
    private OrderStatus status;  // Updated to use OrderStatus enum
    private List<Taco> tacos;
    private List<SideItem> sideItems;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public Date getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Date orderDate) {
        this.orderDate = orderDate;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<Taco> getTacos() {
        return tacos;
    }

    public void setTacos(List<Taco> tacos) {
        this.tacos = tacos;
    }

    public List<SideItem> getSideItems() {
        return sideItems;
    }

    public void setSideItems(List<SideItem> sideItems) {
        this.sideItems = sideItems;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id='" + id + '\'' +
                ", customerId='" + customerId + '\'' +
                ", orderDate=" + orderDate +
                ", totalPrice=" + totalPrice +
                ", status=" + status +
                ", tacos=" + tacos +
                ", sideItems=" + sideItems +
                '}';
    }
}
