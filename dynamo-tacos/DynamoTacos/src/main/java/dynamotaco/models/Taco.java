package dynamotaco.models;

import java.util.List;

public class Taco extends BaseItem{
    private List<Topping> toppings;

    public List<Topping> getToppings() {
        return toppings;
    }

    public void setToppings(List<Topping> toppings) {
        this.toppings = toppings;
    }
}
