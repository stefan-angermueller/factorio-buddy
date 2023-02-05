package de.angermueller.factorio.service;

import java.io.IOException;

public interface RCONClient {

    String sendCommand(String command) throws IOException;

}

