package api.entity.hostile;

import simplepets.brainsynder.api.entity.misc.EntityPetType;
import simplepets.brainsynder.api.entity.misc.IEntityWizard;
import simplepets.brainsynder.api.entity.misc.IRaider;
import simplepets.brainsynder.api.pet.PetType;

@EntityPetType(petType = PetType.ILLUSIONER)
public interface IEntityIllusionerPet extends IEntityWizard, IRaider {
}
