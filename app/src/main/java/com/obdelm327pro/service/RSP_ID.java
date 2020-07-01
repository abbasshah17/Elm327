package com.obdelm327pro.service;

public enum RSP_ID {
    PROMPT(">"),
    OK("OK"),
    MODEL("ELM"),
    NODATA("NODATA"),
    SEARCH("SEARCHING"),
    ERROR("ERROR"),
    NOCONN("UNABLE"),
    NOCONN_MSG("UNABLE TO CONNECT"),
    NOCONN2("NABLETO"),
    CANERROR("CANERROR"),
    CONNECTED("ECU CONNECTED"),
    BUSBUSY("BUSBUSY"),
    BUSY("BUSY"),
    BUSERROR("BUSERROR"),
    BUSINIERR("BUSINIT:ERR"),
    BUSINIERR2("BUSINIT:BUS"),
    BUSINIERR3("BUSINIT:...ERR"),
    BUS("BUS"),
    FBERROR("FBERROR"),
    DATAERROR("DATAERROR"),
    BUFFERFULL("BUFFERFULL"),
    STOPPED("STOPPED"),
    RXERROR("<"),
    QMARK("?"),
    UNKNOWN("");
    String response;

    RSP_ID(String response) {
        this.response = response;
    }

    @Override
    public String toString() {
        return response;
    }
}