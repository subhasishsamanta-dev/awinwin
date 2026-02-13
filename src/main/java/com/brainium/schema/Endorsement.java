package com.brainium.schema;
/**
 * Represents an endorsement with a title and description.
 */

public class Endorsement {
   public EndorsementType type;

   public Endorsement(EndorsementType type) {
       this.type = type;
   }
}