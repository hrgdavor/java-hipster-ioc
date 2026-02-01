package hr.hrg.hipster.ioc.experiment.entity;

public interface User {

    Long id();

    //region fields static definition
    String id = "id"; //

    //endregion

    record Record(Long id) implements User{}

    class Builder implements User{
        Long id;
        public Long id(){return id;}
        public void id(Long v){id = v;}
        public User build(){
            return new Record(id);
        }
    }
}
