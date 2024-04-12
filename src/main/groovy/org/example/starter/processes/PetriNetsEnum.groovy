package org.example.starter.processes

enum PetriNetsEnum {

    final String NET_FILE
    final String NET_IDENTIFIER
    final String NET_NAME
    final String NET_INITIALS

    PetriNetsEnum(String fileName, String identifier, String name, String initials) {
        this.NET_FILE = fileName
        this.NET_IDENTIFIER = identifier
        this.NET_NAME = name
        this.NET_INITIALS = initials
    }
}
