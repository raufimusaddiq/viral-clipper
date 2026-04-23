"""Feature-family modules extracted from score.py during the P3.5-A refactor.

Each submodule exposes standalone feature extractors / scorers that score.py
then orchestrates. The split is structural only — numeric output is identical
to the pre-split monolith (golden-fixture tests cover this).
"""
