package jace.hardware;

import java.io.InputStream;

public class Votrax {
    // This is a speech synthesizer based on the Votrax SC-02
    // There are 2 sound generators, one for the voice and one for the noise
    // The voice generator is a saw-tooth wave generator at a frequency determined by the voice frequency register
    // The noise generator is a pseudo-random noise generator

    // The Votrax has 5 filters that can be applied to the voice generator, controlled by a filter frequency register
    // The fifth filter takes both the voice and noise generators as input, but other filters only take the voice generator
    // There is also a final high-pass filter that can be applied to the output of the voice and noise generators

    // There is a phoneme register which controls the phoneme to be spoken (0-63)
    // There is a duration register which controls the duration of the phoneme (0-3)
    // There is a rate register which controls the rate of speech (0-15)
    // There is an inflection register which controls the inflection of the voice (0-15)

    // For each phoneme there are 8 bytes that control the filters and sound generator levels
    byte[] phonemeData = new byte[64 * 8];
    // Phoneme chart:
    // 00: PA (pause)
    // 01: E (mEEt)
    // 02: E1 (bEnt)
    // 03: Y (bEfore)
    // 04: Y1 (Year)
    // 05: AY (plEAse)
    // 06: IE (anY)
    // 07: I (sIx)
    // 08: A (mAde)
    // 09: A1 (cAre)
    // 0a: EH (nEst)
    // 0b: EH1 (bElt)
    // 0c: AE (dAd)
    // 0d: AE1 (After)
    // 0e: AH (gOt)
    // 0f: AH1 (fAther)
    // 10: AW (Office)
    // 11: O (stOre)
    // 12: OU (bOAt)
    // 13: OO (lOOk)
    // 14: IU (yOU)
    // 15: IU1 (cOUld)
    // 16: U (tUne)
    // 17: U1 (cartOOn)
    // 18: UH (wOnder)
    // 19: UH1 (lOve)
    // 1a: UH2 (whAt)
    // 1b: UH3 (nUt)
    // 1c: ER (bIRd)
    // 1d: R (Roof)
    // 1e: R1 (Rug)
    // 1f: R2 (mutteR -- German)
    // 20: L (Lift)
    // 21: L1 (pLay)
    // 22: LF (faLL)
    // 23: W (Water)
    // 24: B (Bag)
    // 25: D (paiD)
    // 26: KV (taG)
    // 27: P (Pen)
    // 28: T (Tart)
    // 29: K (Kit)
    // 2a: HV - Hold Vocal
    // 2b: HVC - Hold Vocal Closure
    // 2c: HF - (Heart)
    // 2d: HFC - Hold Frictave Closure
    // 2e: HN - Hold Nasal
    // 2f: Z (Zero)
    // 30: S (Same)
    // 31: J (meaSure)
    // 32: SCH (SHip)
    // 33: V (Very)
    // 34: F (Four)
    // 35: THV (THere)
    // 36: TH (wiTH)
    // 37: M (More)
    // 38: N (NiNe)
    // 39: NG (raNG)
    // 3a: :A (mAErchen -- German)
    // 3b: :OH (lOwe - French)
    // 3c: :U (fUEnf -- German)
    // 3d: :UH (menU -- French)
    // 3e: E2 (bittE -- German)
    // 3f: LB (Lube)

    public void loadPhonemeData() {
        InputStream romFile = Votrax.class.getResourceAsStream("/jace/data/sc01a.bin");
        if (romFile == null) {
            throw new RuntimeException("Cannot find Votrax SC-01A ROM");
        }
        // Load into phonemeData
        try {
            if (romFile.read(phonemeData) != phonemeData.length) {
                throw new RuntimeException("Bad Votrax SC-01A ROM size");
            }
        } catch (Exception ex) {
            throw new RuntimeException("Error loading Votrax SC-01A ROM", ex);
        }
    }

}