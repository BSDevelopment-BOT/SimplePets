package api.entity.passive;

import simplepets.brainsynder.api.entity.misc.EntityPetType;
import simplepets.brainsynder.api.entity.misc.IAgeablePet;
import simplepets.brainsynder.api.pet.PetType;

@EntityPetType(petType = PetType.POLARBEAR)
public interface IEntityPolarBearPet extends IAgeablePet {
    void setStandingUp(boolean flag);

    boolean isStanding();
}
