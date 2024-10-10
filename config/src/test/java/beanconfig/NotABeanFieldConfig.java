package beanconfig;

public class NotABeanFieldConfig {

    private NotABean notBean;

    public NotABean getNotBean() {
        return notBean;
    }

    public void setNotBean(NotABean notBean) {
        this.notBean = notBean;
    }

    public static class NotABean {
        int stuff;
    }
}
