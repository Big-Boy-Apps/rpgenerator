#!/usr/bin/env python3
"""
Creative ambient music generator
Layered, evolving, ethereal - inspired by C418/Minecraft but more adventurous
"""

from midiutil import MIDIFile
import random
import math

random.seed(123)

def generate_ambient():
    midi = MIDIFile(4)  # 4 tracks for layering
    tempo = 55
    midi.addTempo(0, 0, tempo)

    # Track 0: Ethereal pad/drone (Pad 2 Warm - program 89)
    midi.addProgramChange(0, 0, 0, 89)
    # Track 1: Piano for melody (program 0)
    midi.addProgramChange(0, 1, 0, 0)
    # Track 2: Celesta/music box sparkles (program 8)
    midi.addProgramChange(0, 2, 0, 8)
    # Track 3: Soft strings for swells (program 48)
    midi.addProgramChange(0, 3, 0, 48)

    duration = 300  # 5 minutes

    # Different modes for variety - each has its own color
    modes = {
        'lydian': [0, 2, 4, 6, 7, 9, 11],      # Dreamy, floating
        'dorian': [0, 2, 3, 5, 7, 9, 10],      # Melancholic but hopeful
        'mixolydian': [0, 2, 4, 5, 7, 9, 10],  # Warm, folk-like
        'aeolian': [0, 2, 3, 5, 7, 8, 10],     # Natural minor, reflective
    }

    def get_scale(root, mode_name):
        intervals = modes[mode_name]
        return [root + i for i in intervals]

    # === LAYER 1: Evolving drone/pad ===
    time = 0
    current_root = 48  # Start on C
    roots = [48, 53, 50, 55, 48, 45, 50, 48]  # C, F, D, G, C, A, D, C - gentle progression
    root_idx = 0

    while time < duration:
        # Change root every ~35-45 seconds
        hold = random.uniform(35, 45)
        root = roots[root_idx % len(roots)]

        # Soft drone - root and fifth
        midi.addNote(0, 0, root, time, hold, 30)
        midi.addNote(0, 0, root + 7, time + 2, hold - 2, 25)

        # Sometimes add the third for warmth
        if random.random() < 0.4:
            third = root + 4 if random.random() < 0.6 else root + 3  # Major or minor third
            midi.addNote(0, 0, third, time + 5, hold - 8, 20)

        time += hold
        root_idx += 1

    # === LAYER 2: Sparse piano melody ===
    time = 8  # Start after drone establishes
    mode_names = list(modes.keys())
    current_mode = 'lydian'
    last_note = None

    while time < duration - 10:
        # Pick a root that matches where we are in the progression
        progress = time / duration
        root = 60 + int(math.sin(progress * math.pi * 4) * 5)  # Gently shift center

        # Change mode occasionally for color
        if random.random() < 0.08:
            current_mode = random.choice(mode_names)

        scale = get_scale(root, current_mode)

        # Decide what to play
        choice = random.random()

        if choice < 0.5:
            # Single note with intention
            if last_note and random.random() < 0.6:
                # Step-wise motion
                step = random.choice([-1, 1, -2, 2])
                idx = scale.index(last_note) if last_note in scale else len(scale) // 2
                idx = max(0, min(len(scale) - 1, idx + step))
                note = scale[idx]
            else:
                note = random.choice(scale)

            velocity = random.randint(38, 58)
            note_dur = random.uniform(2.5, 6)
            midi.addNote(0, 1, note, time, note_dur, velocity)
            last_note = note

        elif choice < 0.7:
            # Two notes - a gentle interval
            note1 = random.choice(scale)
            interval = random.choice([2, 4, 5, 7])  # 3rd, 5th, 4th, octave-ish
            note2 = note1 + interval

            velocity = random.randint(35, 50)
            midi.addNote(0, 1, note1, time, 4, velocity)
            midi.addNote(0, 1, note2, time + 0.3, 3.5, velocity - 8)
            last_note = note1

        elif choice < 0.85:
            # Descending fragment - 3 notes falling
            start_note = random.choice(scale[3:])  # Start higher
            for i in range(3):
                idx = scale.index(start_note) if start_note in scale else 4
                note = scale[max(0, idx - i)]
                vel = 50 - i * 8
                midi.addNote(0, 1, note, time + i * 1.2, 2, vel)
            last_note = note

        else:
            # Ascending hope motif
            start_idx = random.randint(0, 3)
            for i in range(4):
                note = scale[min(start_idx + i, len(scale) - 1)]
                vel = 35 + i * 5
                midi.addNote(0, 1, note, time + i * 0.8, 2.5, vel)
            last_note = note

        # Variable silence - sometimes long contemplative gaps
        if random.random() < 0.15:
            gap = random.uniform(12, 20)  # Long pause
        else:
            gap = random.uniform(3, 9)
        time += gap

    # === LAYER 3: Celesta sparkles ===
    time = 20
    while time < duration - 5:
        if random.random() < 0.35:  # Only sometimes
            root = 72 + random.choice([0, 2, 4, 7, 9])  # High register, pentatonic-ish
            velocity = random.randint(25, 45)

            # Single sparkle
            midi.addNote(0, 2, root, time, 1.5, velocity)

            # Sometimes a little cascade
            if random.random() < 0.3:
                for i in range(random.randint(2, 4)):
                    sparkle = root + random.choice([-5, -3, -2, 2, 3, 5])
                    midi.addNote(0, 2, sparkle, time + 0.4 + i * 0.5, 1.2, velocity - 10 - i * 3)

        time += random.uniform(8, 18)

    # === LAYER 4: String swells at emotional moments ===
    swell_times = [45, 95, 150, 210, 270]  # Key moments
    for t in swell_times:
        if t < duration - 15:
            # Build a chord
            root = 48 + random.choice([0, 5, 7, -5])
            chord = [root, root + 7, root + 12, root + 16]

            # Swell in
            for i, note in enumerate(chord):
                start_vel = 15
                midi.addNote(0, 3, note, t + i * 0.5, 12, start_vel + i * 3)

            # Add motion - one note rises
            if random.random() < 0.6:
                rising = root + 12
                midi.addNote(0, 3, rising + 2, t + 6, 5, 35)

    # === LAYER 5: Occasional deep bass note for grounding ===
    time = 30
    while time < duration - 20:
        if random.random() < 0.4:
            bass = 36 + random.choice([0, 5, 7])  # Low C, F, or G
            midi.addNote(0, 0, bass, time, 8, 28)
        time += random.uniform(25, 45)

    # Save
    with open("ambient.mid", 'wb') as f:
        midi.writeFile(f)
    print("Created: ambient.mid (5 minutes, 4 layers)")

if __name__ == "__main__":
    print("Generating layered ambient music...")
    generate_ambient()
    print("Done!")
