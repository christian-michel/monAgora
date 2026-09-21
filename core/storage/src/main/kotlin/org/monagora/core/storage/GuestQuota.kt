package org.monagora.core.storage

/**
 * Limite anti-abus du mode invité (constitution-technique.md, section 8) :
 * "pas de résistance Sybil possible pour une clé jetable par nature — la
 * limite se pose au niveau de l'appareil (nombre d'objets créés en mode
 * invité par appareil et par jour), pas de l'identité." Un seul compteur par
 * jour pour l'appareil physique — pas de dimension "par pair"/"par identité"
 * ici, contrairement à [org.monagora.core.storage.SignedObjectStore] ou au
 * curseur de synchronisation : c'est tout l'appareil qui partage une seule
 * limite, quel que soit le nombre de sessions invité ouvertes dans la journée.
 *
 * La valeur numérique de la limite elle-même n'est PAS fixée ici — la spec ne
 * la fixe pas non plus, c'est un choix éditorial laissé à l'application
 * (comparer avec le tableau lecture/écriture par application de la même
 * section, explicitement qualifié d'"ajustable librement").
 */
interface GuestQuota {
    /** Nombre d'objets déjà créés en mode invité aujourd'hui (date UTC). */
    fun countToday(): Int

    /**
     * Vérifie la limite puis incrémente le compteur du jour en une seule
     * opération (évite une fenêtre entre la lecture et l'écriture) — à appeler
     * juste avant de créer effectivement un objet en mode invité.
     *
     * @return `true` et incrémente si `countToday() < dailyLimit` ; `false` et
     *   n'incrémente pas sinon.
     */
    fun tryConsume(dailyLimit: Int): Boolean
}
